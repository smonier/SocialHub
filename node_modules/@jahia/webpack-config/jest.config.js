module.exports = {
    setupFilesAfterEnv: [
        '<rootDir>/../../jest.setup.js'
    ],
    transform: {
        '^.+\\.[tj]sx?$': 'babel-jest'
    },
    moduleNameMapper: {
        '\\.(gif|ttf|woff|woff2|eot|eot|svg)$': '<rootDir>/__mocks__/fileMock.js'
    },
    modulePathIgnorePatterns: [
        'build/',
        'target/',
        'node'
    ],
    testPathIgnorePatterns: [
        'build/',
        'node_modules/',
        'target/',
        'node'
    ]
};
