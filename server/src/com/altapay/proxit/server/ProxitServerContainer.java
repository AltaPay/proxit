package com.altapay.proxit.server;

import com.altapay.proxit.server.clients.ProxitServer;

public class ProxitServerContainer
{


	private static ProxitServerContainer instance;
	private PropertiesProxitConfig config;

	protected ProxitServerContainer(
		PropertiesProxitConfig config
	)
	{
		this.config = config;
	}

	public static synchronized ProxitServerContainer initialize(PropertiesProxitConfig config)
	{
		if(instance == null)
		{
			instance = new ProxitServerContainer(config);
		}
		else
		{
			throw new RuntimeException("There should only be one container pr. vm");
		}
		return instance;
	}

	public ProxitServer getProxitServer()
	{
		return new ProxitServer(config);
	}
	
}
