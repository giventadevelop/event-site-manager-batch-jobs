#!/usr/bin/env node
/**
 * Reads Java-style .properties from infrastructure_deployment/environments
 * and returns them as a plain object. Use for deploy scripts or syncing to GitHub.
 *
 * Usage:
 *   node scripts/read-env-props.js
 *   node scripts/read-env-props.js --env=dev
 *   node scripts/read-env-props.js --env=prod
 *   node scripts/read-env-props.js --file=path/to/file.properties
 *   ENV_PROPS_FILE=path node scripts/read-env-props.js
 *
 * Options (attributes):
 *   --env=<name>       Environment profile: dev, prod, staging (default: dev)
 *   --profile=<name>   Alias for --env
 *   --file=<path>      Explicit path to .properties file (overrides --env)
 *   --path=<path>      Alias for --file
 */

const fs = require('fs');
const path = require('path');

const ENVS_DIR = path.join(__dirname, '..', 'infrastructure_deployment', 'environments');
const DEFAULT_ENV = 'dev';
const DEFAULT_PROPS_PATH = path.join(
  ENVS_DIR,
  'event-site-manager-batch-jobs-dev.properties'
);

/**
 * Parse CLI options: --env=, --profile=, --file=, --path=, or positional path
 * @param {string[]} argv - process.argv slice
 * @returns {{ env: string, file: string | null }}
 */
function parseArgs(argv) {
  let env = DEFAULT_ENV;
  let file = null;

  for (const arg of argv) {
    if (arg.startsWith('--env=')) {
      env = arg.slice(6).trim() || DEFAULT_ENV;
    } else if (arg.startsWith('--profile=')) {
      env = arg.slice(10).trim() || DEFAULT_ENV;
    } else if (arg.startsWith('--file=')) {
      file = arg.slice(7).trim() || null;
    } else if (arg.startsWith('--path=')) {
      file = arg.slice(7).trim() || null;
    } else if (!arg.startsWith('--') && arg.trim()) {
      file = file || arg.trim();
    }
  }

  return { env, file };
}

/**
 * Resolve properties file path from env profile (no --file).
 * @param {string} env - dev, prod, staging, etc.
 * @returns {string}
 */
function pathForEnv(env) {
  const name = `event-site-manager-batch-jobs-${env}.properties`;
  return path.join(ENVS_DIR, name);
}

/**
 * Parse Java-style .properties content into an object.
 * - key=value (first = separates key from value)
 * - Lines starting with # or ! are comments
 * - Trims key and value
 * - Empty keys are skipped
 *
 * @param {string} content - Raw file content
 * @returns {Record<string, string>}
 */
function parseProperties(content) {
  const out = {};
  const lines = content.split(/\r?\n/);

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;

    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;

    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (key === '') continue;

    out[key] = value;
  }

  return out;
}

/**
 * Resolve the path to the .properties file from options or env.
 *
 * @param {{ env?: string, file?: string | null }} [options]
 * @returns {string}
 */
function resolvePropsPath(options = {}) {
  if (options.file && options.file.trim()) {
    return path.resolve(options.file.trim());
  }
  if (process.env.ENV_PROPS_FILE) {
    return path.resolve(process.env.ENV_PROPS_FILE);
  }
  const env = (options.env || DEFAULT_ENV).trim() || DEFAULT_ENV;
  return path.resolve(pathForEnv(env));
}

/**
 * Read and parse a .properties file.
 *
 * @param {string | { env?: string, file?: string | null }} [filePathOrOptions] - Path string, or options { env, file }
 * @returns {Record<string, string>}
 * @throws {Error} If file is missing or unreadable
 */
function readEnvProps(filePathOrOptions) {
  let resolved;
  if (filePathOrOptions == null) {
    resolved = resolvePropsPath({});
  } else if (typeof filePathOrOptions === 'string') {
    resolved = path.resolve(filePathOrOptions);
  } else {
    resolved = resolvePropsPath(filePathOrOptions);
  }

  if (!fs.existsSync(resolved)) {
    throw new Error(`Properties file not found: ${resolved}`);
  }

  const content = fs.readFileSync(resolved, 'utf8');
  return parseProperties(content);
}

// Run as CLI: print JSON to stdout
if (require.main === module) {
  const { env, file } = parseArgs(process.argv.slice(2));
  try {
    const props = readEnvProps({ env, file });
    process.stdout.write(JSON.stringify(props, null, 2));
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
} else {
  module.exports = {
    readEnvProps,
    parseProperties,
    parseArgs,
    pathForEnv,
    resolvePropsPath,
    DEFAULT_PROPS_PATH,
    ENVS_DIR,
    DEFAULT_ENV
  };
}
