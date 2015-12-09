package com.altapay.proxit.server;



public interface IProxitConfig
{
	String getGatewayHost();
	short getClientListenPort();
	String getCallbackBaseUrl();
	short getCallbackListenPort();
}
