package mws;

import java.io.File;
import java.util.Locale;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import fs.FileSystemResourceFactory;
import io.milton.http.fs.NullSecurityManager;
import io.milton.simpleton.SimpletonServer;

public class SimpleMiltonServer {
	static int port = 8080;
	static String homeFolder = "D:\\";

	public static void main(String[] args) {
		Locale.setDefault(Locale.ENGLISH);
		NullSecurityManager nsm = new NullSecurityManager();
		FileSystemResourceFactory resourceFactory = new FileSystemResourceFactory(new File(homeFolder), nsm, "/");
		resourceFactory.setAllowDirectoryBrowsing(true);
		HttpManagerBuilder b = new HttpManagerBuilder();
		b.setEnableFormAuth(false);
		b.setResourceFactory(resourceFactory);
		b.setEnableQuota(true);
		HttpManager httpManager = b.buildHttpManager();
		SimpletonServer ss = new SimpletonServer(httpManager, b.getOuterWebdavResponseHandler(), 100, 10);
		ss.setHttpPort(port);
		ss.start();
	}

}
