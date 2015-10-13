package com.altapay.proxit.client;


public class ProxitClientContainer 
{
	private static ProxitClientContainer instance;
	public static synchronized ProxitClientContainer initialize()
	{
		if(instance == null)
		{
			instance = new ProxitClientContainer();
		}
		else
		{
			throw new RuntimeException("There should only be one container pr. vm");
		}
		return instance;
	}
	public ProxitClient getProxitClient(short listenPort, short serverPort, String serverHost)
	{
		return new ProxitClient(listenPort, serverPort, serverHost);
	}
}
