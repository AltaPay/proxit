package com.altapay.proxit;

import java.net.Socket;

public interface ResponseSocketProvider
{
	public Socket getSocket(RawHttpMessage message);
}
