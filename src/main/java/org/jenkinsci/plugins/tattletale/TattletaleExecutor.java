package org.jenkinsci.plugins.tattletale;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * <p>
 * This class is used for tattletale invocation. 
 * 
 * There are three options how can it be achieved:
 * <ul>
 * 	<li> Runtime execution
 *  <li> Using URLClassLoader in runtime
 *  <li> Dynamically adding jar on classpath
 * </ul>
 * </p>
 * 
 * @author Vaclav Tunka
 *
 */
public class TattletaleExecutor {

	private final String workspacePath;
	private final String[] command;
	
	private final AbstractBuild<?,?> build;
	private final BuildListener listener;
	private final TattletaleBuilder tattletaleBuilder;

	public TattletaleExecutor(TattletaleBuilder tattletaleBuilder, AbstractBuild<?,?> build, BuildListener listener) {
		this.tattletaleBuilder = tattletaleBuilder;
		this.build = build;
		this.listener = listener;
		
		workspacePath = initWorkspacePath();
		command = constructCommand();
	}

	public boolean executeTattletale(Launcher launcher) {
		boolean successfull = true;
		
		successfull = (workspacePath != null);
		if (!successfull) return successfull;
		
		listener.getLogger().println("[Tattletale] Workspace path: " + workspacePath);
		
		try {
			int exitCode = launcher.launch().cmds(command).envs(build.getEnvironment(listener)).pwd(build.getWorkspace()).stdout(listener).join();
			successfull = (exitCode == 0);
		} catch (IOException e) {
			listener.getLogger().println("[Tattletale] Error running tattletale.");
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (InterruptedException e) {
			listener.getLogger().println("[Tattletale] Error running tattletale - interrupted.");
			listener.getLogger().println(e.getMessage());
			return false;
		}
		
		return successfull;
	}

	private String[] constructCommand() {
		String jar = tattletaleBuilder.getDescriptor().getTattletaleJarLocation();
		String input  = workspacePath + File.separator + tattletaleBuilder.getInputDirectory();
		String output = workspacePath + File.separator + tattletaleBuilder.getOutputDirectory();
		return new String[]{"java", "-jar", jar, input, output};
	}
	
	private String initWorkspacePath() {
		String workspace = "";
		try {
			workspace = build.getWorkspace().absolutize().getRemote();
		} catch (IOException e) {
			listener.getLogger().println("[Tattletale] Error running tattletale.");
			listener.getLogger().println(e.getMessage());
			return null;
		} catch (InterruptedException e) {
			listener.getLogger().println("[Tattletale] Error running tattletale.");
			listener.getLogger().println(e.getMessage());
			return null;
		}
		
		return workspace;
	}
	
	/**
	 * Tries to load tattletale jar at runtime using URLClassloader and reflection
	 */
	private boolean loadTattletale(TattletaleBuilder tattletaleBuilder, AbstractBuild<?,?> build, BuildListener listener) {
		File tattletaleExecutable = new File(tattletaleBuilder.getDescriptor().getTattletaleJarLocation());
		URL url = null;
		
		try {
			url = tattletaleExecutable.toURI().toURL();
		} catch (MalformedURLException e) {
			listener.getLogger().println("Malformed tattletale executable path");
			e.printStackTrace();
			listener.getLogger().println(e.getMessage());
			return false;
		}
	    
		URL[] urlCollection = new URL[]{url};
		
		ClassLoader cl = new URLClassLoader(urlCollection);
		
		Class<?> clazz = null;
		
		try {
			clazz = cl.loadClass("org.jboss.tattletale.Main");
		} catch (ClassNotFoundException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}
			
		Constructor<?> ctor;
		try {
			ctor = clazz.getConstructor();
		} catch (SecurityException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (NoSuchMethodException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}
		
		Object tattletaleInstance;
		try {
			tattletaleInstance = ctor.newInstance();
		} catch (IllegalArgumentException e) {
			e.getLocalizedMessage();
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (InstantiationException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (IllegalAccessException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (InvocationTargetException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}
		
		Method setSourceMethod = null;
		Method setDestinationMethod = null;
		Method executeMethod = null;
		try {
			setSourceMethod = clazz.getMethod("setSource");
			setDestinationMethod = clazz.getMethod("setDestination");
			executeMethod = clazz.getMethod("execute");
		} catch (SecurityException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (NoSuchMethodException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}
		
		try {
			setSourceMethod.invoke(tattletaleInstance, ".");
			setDestinationMethod.invoke(tattletaleInstance, "tattletale-report");
			executeMethod.invoke(tattletaleInstance);
		} catch (IllegalArgumentException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (IllegalAccessException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		} catch (InvocationTargetException e) {
			listener.getLogger().println(e.getMessage());
			return false;
		}
		
		return true;
	}

	/**
	 * Add the tattletale jar to classpath in runtime
	 */
	public static void addToClasspath(String s) throws Exception {
		File f = new File(s);
		URL u = f.toURI().toURL();
		URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader
				.getSystemClassLoader();
		Class<URLClassLoader> urlClass = URLClassLoader.class;
		Method method = urlClass.getDeclaredMethod("addURL",
				new Class[] { URL.class });
		method.setAccessible(true);
		method.invoke(urlClassLoader, new Object[] { u });
	}

}
