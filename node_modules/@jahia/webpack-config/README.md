<h1 align="center">Welcome to @jahia/webpack-config 👋</h1>
<p>
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.0-blue.svg?cacheSeconds=2592000" />
  <a href="../../LICENSE.txt" target="_blank">
    <img alt="License: SEE LICENSE IN ../../LICENSE.txt FILE" src="https://img.shields.io/badge/License-SEE LICENSE IN ../../LICENSE.txt FILE-yellow.svg" />
  </a>
  <a href="https://twitter.com/Jahia" target="_blank">
    <img alt="Twitter: Jahia" src="https://img.shields.io/twitter/follow/Jahia.svg?style=social" />
  </a>
</p>

> Provide helpers to generates a ModuleFederation plugin config for Jahia extensions

## Usage

In your webpack.config.js :

```javascript
const getModuleFederationConfig = require('@jahia/webpack-config/getModuleFederationConfig');
const packageJson = require('./package.json');
```

```javascript
        ...
        plugins: [
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson)),
            ...
        ],
        ...
```

This will create a default configuration for your module : 
- remote name will be taken from package.json
- remote will be added to `appShell.remotes`
- `./src/javascript/init` will be exposed as `./init`, in order to be detected and initialized by app-shell
- shared libraries will be configured based on a global config and your package.json
- shared libraries are by default not imported (not bundled)


It's possible to pass custom configuration as an optional second argument :

```javascript
        ...
        plugins: [
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson, {
                name: 'customName',
                exposes: {
                    '.': './src/javascript/shared',
                },
                remotes: {
                    '@jahia/jahia-ui-root': 'appShell.remotes.jahiaUi'
                },
                shared: {
                    'my-additional-shared-lib': '^1.0.0'
                }
            })),
            ...
        ],
        ...
```

You can also specify the list of shared libraries you want to import, as a third argument :

```javascript
        ...
        plugins: [
            new ModuleFederationPlugin(getModuleFederationConfig(packageJson, {}, [
                'react', 'react-dom', '@jahia/moonstone'
            ])),
            ...
        ],
        ...
```

Note : if you want to import all shared libraries you have, you can use `getModuleFederationConfig(packageJson, {}, Object.keys(packageJson.dependencies))`
