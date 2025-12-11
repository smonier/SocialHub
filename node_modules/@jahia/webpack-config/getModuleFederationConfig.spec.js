import getModuleFederationConfig from './getModuleFederationConfig';

const packageJson = {
    name: '@jahia/test-name',
    version: '1.0.0',
    description: 'Webpack shareable config for Jahia project',
    main: 'index.js',
    license: 'MIT',
    dependencies: {
        react: '^16.1.5',
        i18next: '^16',
        dayjs: '1.8.21'
    }
};

describe('getModuleFederationConfig', () => {
    it('should create base config', () => {
        const config = getModuleFederationConfig(packageJson);
        expect(config.name).toBe('testName');
        expect(config.library.name).toBe('appShell.remotes.testName');
        expect(config.shared.i18next.requiredVersion).toBe(packageJson.dependencies.i18next);
        expect(config.shared.i18next.import).toBe(false);
        expect(config.shared.i18next.singleton).toBe(true);
        expect(config.shared.dayjs.requiredVersion).toBe(packageJson.dependencies.dayjs);
        expect(config.shared.dayjs.import).toBe(false);
        expect(config.shared.dayjs.singleton).toBe(undefined);
        expect(config.exposes['./init']).toBe('./src/javascript/init');
        expect(config.remotes['@jahia/app-shell']).toBe('appShellRemote');
    });

    it('should take custom config', () => {
        const config = getModuleFederationConfig(packageJson, {name: 'customName'});
        expect(config.name).toBe('customName');
    });

    it('should accept import', () => {
        const config = getModuleFederationConfig(packageJson, {}, ['i18next', 'dayjs']);
        expect(config.shared.i18next.requiredVersion).toBe(packageJson.dependencies.i18next);
        expect(config.shared.i18next.import).toBe(undefined);
        expect(config.shared.i18next.singleton).toBe(true);
        expect(config.shared.dayjs.requiredVersion).toBe(packageJson.dependencies.dayjs);
        expect(config.shared.dayjs.import).toBe(undefined);
        expect(config.shared.dayjs.singleton).toBe(undefined);
    });

    it('should accept additional remotes and exposes', () => {
        const config = getModuleFederationConfig(packageJson, {
            exposes: {
                '.': './src/javascript/shared'
            },
            remotes: {
                '@jahia/jcontent': 'appShell.remotes.jcontent',
                '@jahia/jahia-ui-root': 'appShell.remotes.jahiaUi'
            }
        }, []);
        expect(config.exposes['./init']).toBe('./src/javascript/init');
        expect(config.exposes['.']).toBe('./src/javascript/shared');
        expect(config.remotes['@jahia/app-shell']).toBe('appShellRemote');
        expect(config.remotes['@jahia/jcontent']).toBe('appShell.remotes.jcontent');
        expect(config.remotes['@jahia/jahia-ui-root']).toBe('appShell.remotes.jahiaUi');
    });

    it('should accept additional shared libs', () => {
        const config = getModuleFederationConfig(packageJson, {
            shared: {
                myLib: '^1.0.0'
            }
        }, []);
        expect(config.shared.i18next.requiredVersion).toBe(packageJson.dependencies.i18next);
        expect(config.shared.i18next.import).toBe(false);
        expect(config.shared.i18next.singleton).toBe(true);
        expect(config.shared.dayjs.requiredVersion).toBe(packageJson.dependencies.dayjs);
        expect(config.shared.dayjs.import).toBe(false);
        expect(config.shared.myLib).toBe('^1.0.0');
    });

    it('should create base config', () => {
        const config = getModuleFederationConfig(packageJson, {}, ['i18next', 'dayjs']);
        expect(config.shared.react.requiredVersion).toBe('>=16.1.5 <19.0.0-0');
    });
});
