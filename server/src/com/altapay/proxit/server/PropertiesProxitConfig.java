package com.altapay.proxit.server;

import java.util.Properties;

public class PropertiesProxitConfig implements IProxitConfig {

	protected Properties properties;
	private boolean isDev = true;

	public PropertiesProxitConfig(Properties properties) {
		this.properties = properties;
	}

	@Override
	public String getGatewayHost() {
		if(isDev)
		{
			return properties.getProperty("gatewayHost","gateway.dev.pensio.com");
		}
		return properties.getProperty("gatewayHost","testgateway.altapaysecure.com");
	}

	@Override
	public boolean getGatewaySsl()
	{
		if(isDev)
		{
			return "true".equals(properties.getProperty("gatewaySsl","false"));
		}
		return "true".equals(properties.getProperty("gatewaySsl","true"));
	}

	@Override
	public short getClientListenPort()
	{
		return Short.parseShort(properties.getProperty("clientListenPort","8089"));
	}

	@Override
	public String getCallbackBaseUrl()
	{
		if(isDev)
		{
			return properties.getProperty("gatewayHost","http://localhost:8080");
		}
		return properties.getProperty("gatewayHost","http://proxitremote.devaltapaysecure.dk:8080");
	}

	@Override
	public short getCallbackListenPort()
	{
		return Short.parseShort(properties.getProperty("callbackListenPort","8080"));
	}
}
