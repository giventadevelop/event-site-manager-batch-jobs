#!/usr/bin/env node
/**
 * Reads a .properties file (via --env/--file) and creates/updates GitHub
 * Environment variables for the given repository and environment.
 * Requires GIT_PAT_TOKEN in .env or environment.
 *
 * Usage:
 *   node scripts/sync-env-vars-to-github.js --env=dev
 *   node scripts/sync-env-vars-to-github.js --env=dev --file=infrastructure_deployment/environments/event-site-manager-batch-jobs-dev.properties
 *   node scripts/sync-env-vars-to-github.js --env=prod --repo=owner/repo --environment=prod
 *
 * Options:
 *   --env=<name>         Environment profile for picking .properties file (default: dev)
 *   --file=<path>        Path to .properties file (overrides --env for file location)
 *   --repo=<owner/repo>  GitHub repo (default: giventadeveloporg/event-site-manager-batch-jobs)
 *   --environment=<name>  GitHub Environment name (default: same as --env, e.g. dev)
 *
 * Requires: .env with GIT_PAT_TOKEN (or set GIT_PAT_TOKEN in shell).
 */

const fs = require('fs');
const path = require('path');
const https = require('https');

// Load .env from project root (simple parser; strips surrounding quotes from values)
function loadEnv() {
  const envPath = path.resolve(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  const content = fs.readFileSync(envPath, 'utf8');
  content.split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const eq = trimmed.indexOf('=');
    if (eq === -1) return;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (key && !process.env[key]) process.env[key] = value;
  });
}

loadEnv();

const { readEnvProps } = require('./read-env-props.js');

const GITHUB_API = 'api.github.com';
const DEFAULT_REPO = 'giventadeveloporg/event-site-manager-batch-jobs';

function parseSyncArgs(argv) {
  let env = 'dev';
  let file = null;
  let repo = DEFAULT_REPO;
  let environment = null;

  for (const arg of argv) {
    if (arg.startsWith('--env=')) env = arg.slice(6).trim() || 'dev';
    else if (arg.startsWith('--file=')) file = arg.slice(7).trim() || null;
    else if (arg.startsWith('--repo=')) repo = arg.slice(7).trim() || DEFAULT_REPO;
    else if (arg.startsWith('--environment=')) environment = arg.slice(14).trim() || null;
  }
  if (!environment) environment = env;
  return { env, file, repo, environment };
}

// Use "token TOKEN" for classic PAT, "Bearer TOKEN" for fine-grained/OAuth (GitHub accepts both)
function authHeader(token) {
  const t = token.trim();
  if (t.toLowerCase().startsWith('bearer ')) return t;
  if (t.toLowerCase().startsWith('token ')) return t;
  return `Bearer ${t}`;
}

function httpsRequest(options, body) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: GITHUB_API,
        path: options.path,
        method: options.method || 'GET',
        headers: {
          'User-Agent': 'event-site-manager-batch-jobs-sync',
          Accept: 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
          Authorization: authHeader(options.token),
          ...(body && { 'Content-Type': 'application/json' }),
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => {
          try {
            const parsed = data ? JSON.parse(data) : {};
            if (res.statusCode >= 400) {
              reject(new Error(`GitHub API ${res.statusCode}: ${parsed.message || data}`));
            } else {
              resolve(parsed);
            }
          } catch (e) {
            if (res.statusCode >= 400) reject(new Error(`GitHub API ${res.statusCode}: ${data}`));
            else resolve(data);
          }
        });
      }
    );
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function getAuthenticatedUser(token) {
  const res = await httpsRequest({ path: '/user', method: 'GET', token });
  return res.login || res.name || 'unknown';
}

async function listEnvironmentVariables(owner, repo, environmentName, token) {
  const pathEnc = encodeURIComponent(environmentName);
  const path = `/repos/${owner}/${repo}/environments/${pathEnc}/variables?per_page=100`;
  const res = await httpsRequest({ path, method: 'GET', token });
  return res.variables || [];
}

async function createVariable(owner, repo, environmentName, name, value, token) {
  const pathEnc = encodeURIComponent(environmentName);
  const path = `/repos/${owner}/${repo}/environments/${pathEnc}/variables`;
  return httpsRequest({ path, method: 'POST', token }, { name, value });
}

async function updateVariable(owner, repo, environmentName, name, value, token) {
  const pathEnc = encodeURIComponent(environmentName);
  const nameEnc = encodeURIComponent(name);
  const path = `/repos/${owner}/${repo}/environments/${pathEnc}/variables/${nameEnc}`;
  return httpsRequest({ path, method: 'PATCH', token }, { value });
}

async function main() {
  const token = process.env.GIT_PAT_TOKEN;
  if (!token || !token.trim()) {
    console.error('Missing GIT_PAT_TOKEN. Set it in .env or environment.');
    process.exit(1);
  }

  const argv = process.argv.slice(2);
  const { env, file, repo, environment } = parseSyncArgs(argv);
  const [owner, repoName] = repo.split('/');
  if (!owner || !repoName) {
    console.error('--repo must be owner/repo (e.g. giventadeveloporg/event-site-manager-batch-jobs)');
    process.exit(1);
  }

  let props;
  try {
    props = readEnvProps({ env, file });
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }

  const keys = Object.keys(props);
  if (keys.length === 0) {
    console.error('No variables found in properties file.');
    process.exit(1);
  }

  const tokenTrimmed = token.trim();
  try {
    const login = await getAuthenticatedUser(tokenTrimmed);
    console.log(`Token OK (user: ${login}). Syncing ${keys.length} variables to GitHub environment "${environment}" (${repo})...`);
  } catch (authErr) {
    if (authErr.message.includes('401')) {
      console.error('Token rejected by GitHub (401). Check .env and token value.');
      console.error('');
      console.error('  • .env must be in project root: ' + path.resolve(__dirname, '..'));
      console.error('  • Line:  GIT_PAT_TOKEN=ghp_xxxxxxxxxxxx   (no quotes, no spaces)');
      console.error('  • If the repo is under an ORGANIZATION (e.g. giventadeveloporg), the token must have access to that org:');
      console.error('    - Fine-grained: create the token under the org, or use "Only select repositories" and add this repo');
      console.error('    - Classic PAT: ensure "repo" scope and that your user can access the org repo');
      process.exit(1);
    }
    throw authErr;
  }

  let existing;
  try {
    existing = await listEnvironmentVariables(owner, repoName, environment, tokenTrimmed);
  } catch (e) {
    console.error('Failed to list environment variables:', e.message);
    if (e.message.includes('401')) {
      console.error('');
      console.error('Token works for GitHub but got 401 on this repo. For repo ' + repo + ':');
      console.error('  • Fine-grained token must have "Environments: Read and write" and access to this repository.');
      console.error('  • If the repo is under an organization, create the token under that org or add this repo in "Only select repositories".');
    }
    process.exit(1);
  }

  const existingNames = new Set((existing || []).map((v) => v.name));

  for (const name of keys) {
    const value = String(props[name]);
    try {
      if (existingNames.has(name)) {
        await updateVariable(owner, repoName, environment, name, value, token.trim());
        console.log(`  Updated: ${name}`);
      } else {
        await createVariable(owner, repoName, environment, name, value, token.trim());
        console.log(`  Created: ${name}`);
      }
    } catch (e) {
      console.error(`  Failed ${name}:`, e.message);
      process.exit(1);
    }
  }

  console.log('Done. Check Settings → Environments → <your env> → Environment variables.');
}

main();
