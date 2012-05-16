package org.jenkinsci.plugins.tattletale;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.Extension;
import hudson.EnvVars;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Messages;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.AncestorInPath;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class TattletaleReportArchiver extends Recorder {
	 /**
     * Path to the Tattletale report directory in the workspace.
     */
    private final String tattletaleReportDir;
    /**
     * If true, retain Tattletale report for all the successful builds.
     */
    private final boolean keepAll;
    
    @DataBoundConstructor
    public TattletaleReportArchiver(String storeDir, boolean keepAll) {
        this.tattletaleReportDir = storeDir;
        this.keepAll = keepAll;
    }

    public String getTattletaleReportDir() {
        return tattletaleReportDir;
    }

    public boolean isKeepAll() {
        return keepAll;
    }

    /**
     * Gets the directory where the Tattletale reports are stored for the given project.
     */
    private static File getTattletaleReportDir(AbstractItem project) {
        return new File(project.getRootDir(),"tattletale-report");
    }

    /**
     * Gets the directory where the Tattletale reports are stored for the given build.
     */
    private static File getTattletaleReportDir(Run run) {
        return new File(run.getRootDir(),"tattletale-report");
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("[Tattletale] Publishing");
        
        FilePath ws = build.getWorkspace();
        if (ws==null) { 
            listener.getLogger().println("[Data-processing] Workspace missing.");
            return true;
        }

        EnvVars env = build.getEnvironment(listener);
        
        FilePath tattletaleReport = build.getWorkspace().child(env.expand(tattletaleReportDir));
        FilePath target = new FilePath(keepAll ? getTattletaleReportDir(build) : getTattletaleReportDir(build.getProject()));

        try {
            if (tattletaleReport.copyRecursiveTo("**/*",target)==0) {
            	listener.error("[Tattletale] No data copied, configuration error?");
                build.setResult(Result.FAILURE);
                return true;
            } else {
//            	build.addAction(new TattletaleAction(build.getRootDir(), plotOpts));
            }
        } catch (IOException e) {
        	e.printStackTrace(listener.error("[Tattletale] Failed to record data"));
            Util.displayIOException(e,listener);
            build.setResult(Result.FAILURE);
            return true;
        }
        
        // add build action, if tattletale report is recorded for each build
        if(keepAll)
            build.addAction(new TattletaleBuildAction(build));
        
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    @Override
    public DescriptorImpl getDescriptor(){
        return (DescriptorImpl)super.getDescriptor();
    }
    
    @Override
    public Collection<Action> getProjectActions(AbstractProject project) {
        return Collections.<Action>singleton(new TattletaleAction(project));
    }
    
    protected static abstract class BaseTattletaleAction implements Action {
        public String getUrlName() {
            return "tattletale";
        }

        public String getDisplayName() {
            return "Tattletale plugin";
        }

        public String getIconFileName() {
            File dir = dir();
            if(dir != null && dir.exists())
                return "help.gif";
            else
                return null;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new DirectoryBrowserSupport(this, new FilePath(dir()), getTitle(), "help.gif", false).generateResponse(req,rsp,this);
        }

        protected abstract String getTitle();

        protected abstract File dir();
    }

    public static class TattletaleAction extends BaseTattletaleAction implements ProminentProjectAction {
        private final AbstractItem project;

        public TattletaleAction(AbstractItem project) {
            this.project = project;
        }

        protected File dir() {
            if (project instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) project;

                Run run = abstractProject.getLastSuccessfulBuild();
                if (run != null) {
                    File tattletaleReportDir = getTattletaleReportDir(run);

                    if (tattletaleReportDir.exists())
                        return tattletaleReportDir;
                }
            }

            return getTattletaleReportDir(project);
        }

        protected String getTitle() {
            return project.getDisplayName() + " Tattletale report";
        }
    }
    
    public static class TattletaleBuildAction extends BaseTattletaleAction {
    	private final AbstractBuild<?,?> build;
    	
    	public TattletaleBuildAction(AbstractBuild<?,?> build) {
    	    this.build = build;
    	}

        protected String getTitle() {
            return build.getDisplayName() + " tattletale report";
        }

        protected File dir() {
            return getTattletaleReportDir(build);
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    	
    	public static final String DEFAULT_DIR = "tattletale-report";
    	private String storeDir;
    	
    	public DescriptorImpl() {
            super(TattletaleReportArchiver.class);
            load();
        }
    	
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            String p = json.getString("storeDir");
            p = Util.fixEmptyAndTrim(p);
            storeDir = (p == null) ? DEFAULT_DIR : p;
            save();
            return super.configure(req, json);
        }
    	
        public String getStoreDir(){
            if(storeDir == null)
                return DEFAULT_DIR;
            return storeDir;
        }
        
        public String getDisplayName(){
            return "Publish Tattletale report";
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> jobType){
            return true;
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, ServletException {
            FilePath ws = project.getSomeWorkspace();
            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
        }
    }
}
