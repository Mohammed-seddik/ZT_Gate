const express = require('express');
const session = require('express-session');
const mysql = require('mysql2/promise');
const crypto = require('crypto');
const { Issuer, generators } = require('openid-client');

const app = express();
app.set('view engine', 'ejs');
app.set('trust proxy', 1);
app.use(express.urlencoded({ extended: false }));
app.use(express.json());
app.use(session({
  secret: process.env.SESSION_SECRET || 'demo-secret',
  resave: false,
  saveUninitialized: false,
  cookie: { secure: true }
}));

let db;
let oidcClient;

async function initDb() {
  db = await mysql.createPool({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    waitForConnections: true,
    connectionLimit: 10
  });
}

async function initOidc() {
  const issuer = await Issuer.discover(process.env.OIDC_ISSUER);
  oidcClient = new issuer.Client({
    client_id: process.env.OIDC_CLIENT_ID,
    client_secret: process.env.OIDC_CLIENT_SECRET,
    redirect_uris: [process.env.OIDC_REDIRECT_URI],
    response_types: ['code']
  });
}

function ensureAuth(req, res, next) {
  if (!req.session.user) return res.redirect('/login');
  return next();
}

app.get('/login', (req, res) => {
  const state = generators.state();
  const nonce = generators.nonce();
  req.session.state = state;
  req.session.nonce = nonce;
  const authUrl = oidcClient.authorizationUrl({
    scope: 'openid profile email roles db-user-id',
    state,
    nonce
  });
  res.redirect(authUrl);
});

app.get('/callback', async (req, res) => {
  try {
    const params = oidcClient.callbackParams(req);
    const tokenSet = await oidcClient.callback(process.env.OIDC_REDIRECT_URI, params, {
      state: req.session.state,
      nonce: req.session.nonce
    });
    const claims = tokenSet.claims();
    req.session.user = {
      username: claims.preferred_username,
      email: claims.email,
      name: claims.name,
      dbUserId: claims.db_user_id,
      roles: claims.realm_access?.roles || []
    };
    res.redirect('/');
  } catch (err) {
    res.status(500).send(`OIDC callback failed: ${err.message}`);
  }
});

app.get('/logout', (req, res) => {
  req.session.destroy(() => res.redirect('/'));
});

app.get('/', (req, res) => {
  if (!req.session.user) {
    return res.render('public');
  }
  return res.render('home', { user: req.session.user });
});

app.get('/protected', ensureAuth, (req, res) => {
  res.render('home', { user: req.session.user });
});

app.post('/auth/verify', async (req, res) => {
  const auth = req.get('authorization') || '';
  const expected = `Bearer ${process.env.SHARED_API_KEY}`;

  console.log('[auth/verify] called', {
    username: req.body?.username,
    remote: req.ip
  });

  if (auth !== expected) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const { username, password } = req.body;
  const [rows] = await db.execute(
    'SELECT id, username, email, display_name, password_hash, role FROM users WHERE username = ? LIMIT 1',
    [username]
  );

  if (!rows.length) {
    return res.json({ valid: false });
  }

  const user = rows[0];
  const hash = crypto.createHash('sha256').update(password).digest('hex');
  const ok = hash === user.password_hash;
  if (!ok) {
    return res.json({ valid: false });
  }

  return res.json({
    valid: true,
    userId: user.id,
    email: user.email,
    name: user.display_name,
    roles: [user.role]
  });
});

(async () => {
  await initDb();
  await initOidc();
  const port = Number(process.env.PORT || 3000);
  app.listen(port, () => console.log(`client-app listening on ${port}`));
})();
