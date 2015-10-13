package com.altapay.proxit.server;



public interface IProxitConfig
{
	String getGatewayHost();
	boolean getGatewaySsl();
	short getClientListenPort();
	String getCallbackBaseUrl();
	short getCallbackListenPort();
}
