package com.altapay.proxit;

public interface ProxitConnectionHandler
{

	void removeConnection(ProxitConnection connection);

	void proxyRequest(ProxitConnection connection, RawHttpMessage request);

	void proxyResponse(RawHttpMessage response);

}
