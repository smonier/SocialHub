import React, {useState} from 'react';
import {useTranslation} from 'react-i18next';
import PropTypes from 'prop-types';
import {
    Button,
    Input,
    Dropdown,
    Typography,
    Loader
} from '@jahia/moonstone';
import {Check, Warning} from '@jahia/moonstone/dist/icons';

const ProxyTestPanel = ({onRequestComplete}) => {
    const {t} = useTranslation('SocialHub');
    const [method, setMethod] = useState('GET');
    const [relativePath, setRelativePath] = useState('/posts');
    const [provider, setProvider] = useState('default');
    const [requestBody, setRequestBody] = useState('');
    const [loading, setLoading] = useState(false);
    const [response, setResponse] = useState(null);
    const [error, setError] = useState(null);

    const handleSubmit = async () => {
        setLoading(true);
        setError(null);
        setResponse(null);

        let url = `/modules/social-proxy${relativePath}`;
        // Append provider selector without altering user query parameters
        const hasQuery = url.includes('?');
        const providerParam = provider && provider !== 'default' ? `${hasQuery ? '&' : '?'}provider=${provider}` : '';
        url = `${url}${providerParam}`;
        const options = {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json'
            }
        };

        if (method === 'POST' && requestBody) {
            try {
                // Validate JSON before sending
                JSON.parse(requestBody);
                options.body = requestBody;
            } catch (_) {
                setError('Invalid JSON in request body');
                setLoading(false);
                return;
            }
        }

        const startTime = Date.now();

        try {
            const res = await fetch(url, options);
            const duration = Date.now() - startTime;
            const contentType = res.headers.get('content-type');

            let data;
            if (contentType && contentType.includes('application/json')) {
                data = await res.json();
            } else {
                data = await res.text();
            }

            if (res.ok) {
                setResponse({
                    status: res.status,
                    statusText: res.statusText,
                    data: data,
                    duration: duration
                });

                // Notify parent for activity log
                if (onRequestComplete) {
                    onRequestComplete({
                        method,
                        path: relativePath,
                        status: 'success',
                        statusCode: res.status,
                        timestamp: new Date().toISOString()
                    });
                }
            } else {
                setError({
                    status: res.status,
                    statusText: res.statusText,
                    data: data,
                    duration: duration
                });

                if (onRequestComplete) {
                    onRequestComplete({
                        method,
                        path: relativePath,
                        status: 'error',
                        statusCode: res.status,
                        timestamp: new Date().toISOString()
                    });
                }
            }
        } catch (err) {
            setError({
                message: err.message,
                type: 'Network Error'
            });

            if (onRequestComplete) {
                onRequestComplete({
                    method,
                    path: relativePath,
                    status: 'error',
                    statusCode: 0,
                    timestamp: new Date().toISOString()
                });
            }
        } finally {
            setLoading(false);
        }
    };

    const methodOptions = [
        {label: 'GET', value: 'GET'},
        {label: 'POST', value: 'POST'}
    ];
    const providerOptions = [
        {label: 'Default', value: 'default'},
        {label: 'Facebook Graph', value: 'facebook'},
        {label: 'Instagram Graph', value: 'instagram'},
        {label: 'LinkedIn API', value: 'linkedin'}
    ];

    return (
        <div style={{padding: '24px'}}>
            <Typography variant="subheading" weight="bold" style={{marginBottom: '16px'}}>
                {t('proxyTest.title')}
            </Typography>
            <Typography variant="body" style={{marginBottom: '24px', color: '#676767'}}>
                {t('proxyTest.description')}
            </Typography>

            <div style={{marginBottom: '16px'}}>
                <Typography variant="caption" style={{marginBottom: '8px', display: 'block'}}>
                    {t('proxyTest.method')}
                </Typography>
                <Dropdown
                    value={method}
                    data={methodOptions}
                    size="medium"
                    style={{width: '200px'}}
                    onChange={(e, item) => setMethod(item.value)}
                />
            </div>

            <div style={{marginBottom: '16px'}}>
                <Typography variant="caption" style={{marginBottom: '8px', display: 'block'}}>
                    {t('proxyTest.target')}
                </Typography>
                <Dropdown
                    value={provider}
                    data={providerOptions}
                    size="medium"
                    style={{width: '240px'}}
                    onChange={(e, item) => setProvider(item.value)}
                />
            </div>

            <div style={{marginBottom: '16px'}}>
                <Typography variant="caption" style={{marginBottom: '8px', display: 'block'}}>
                    {t('proxyTest.path')}
                </Typography>
                <Input
                    fullWidth
                    value={relativePath}
                    placeholder={t('proxyTest.pathPlaceholder')}
                    onChange={e => setRelativePath(e.target.value)}
                />
            </div>

            {method === 'POST' && (
                <div style={{marginBottom: '16px'}}>
                    <Typography variant="caption" style={{marginBottom: '8px', display: 'block'}}>
                        {t('proxyTest.body')}
                    </Typography>
                    <textarea
                        value={requestBody}
                        placeholder={t('proxyTest.bodyPlaceholder')}
                        rows={6}
                        style={{
                            width: '100%',
                            fontFamily: 'monospace',
                            fontSize: '13px',
                            padding: '8px',
                            border: '1px solid #d0d0d0',
                            borderRadius: '4px',
                            resize: 'vertical'
                        }}
                        onChange={e => setRequestBody(e.target.value)}
                    />
                </div>
            )}

            <Button
                label={t('proxyTest.sendRequest')}
                color="accent"
                size="big"
                isDisabled={loading || !relativePath}
                style={{marginBottom: '24px'}}
                onClick={handleSubmit}
            />

            {loading && (
                <div style={{display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px'}}>
                    <Loader size="small"/>
                    <Typography variant="body">Sending request...</Typography>
                </div>
            )}

            {response && (
                <div style={{
                    padding: '16px',
                    backgroundColor: '#e8f5e9',
                    borderRadius: '4px',
                    marginBottom: '16px'
                }}
                >
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px'}}>
                        <Check size="small" style={{color: '#2e7d32'}}/>
                        <Typography variant="subheading" style={{color: '#2e7d32'}}>
                            Success - {response.status} {response.statusText}
                        </Typography>
                        <Typography variant="caption" style={{marginLeft: 'auto', color: '#676767'}}>
                            {response.duration}ms
                        </Typography>
                    </div>
                    <div style={{
                        backgroundColor: '#fff',
                        padding: '12px',
                        borderRadius: '4px',
                        maxHeight: '400px',
                        overflow: 'auto'
                    }}
                    >
                        <pre style={{margin: 0, fontSize: '12px', fontFamily: 'monospace'}}>
                            {typeof response.data === 'string' ?
                                response.data :
                                JSON.stringify(response.data, null, 2)}
                        </pre>
                    </div>
                </div>
            )}

            {error && (
                <div style={{
                    padding: '16px',
                    backgroundColor: '#ffebee',
                    borderRadius: '4px',
                    marginBottom: '16px'
                }}
                >
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px'}}>
                        <Warning size="small" style={{color: '#c62828'}}/>
                        <Typography variant="subheading" style={{color: '#c62828'}}>
                            {error.type || 'Error'} {error.status ? `- ${error.status} ${error.statusText}` : ''}
                        </Typography>
                        {error.duration && (
                            <Typography variant="caption" style={{marginLeft: 'auto', color: '#676767'}}>
                                {error.duration}ms
                            </Typography>
                        )}
                    </div>
                    <div style={{
                        backgroundColor: '#fff',
                        padding: '12px',
                        borderRadius: '4px',
                        maxHeight: '400px',
                        overflow: 'auto'
                    }}
                    >
                        <pre style={{margin: 0, fontSize: '12px', fontFamily: 'monospace'}}>
                            {error.message || (typeof error.data === 'string' ?
                                error.data :
                                JSON.stringify(error.data, null, 2))}
                        </pre>
                    </div>
                </div>
            )}
        </div>
    );
};

ProxyTestPanel.propTypes = {
    onRequestComplete: PropTypes.func
};

ProxyTestPanel.defaultProps = {
    onRequestComplete: null
};

export default ProxyTestPanel;
