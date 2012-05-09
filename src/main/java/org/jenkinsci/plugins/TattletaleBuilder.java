package org.jenkinsci.plugins;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link TattletaleBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #inputDirectory})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Vaclav Tunka
 */
public class TattletaleBuilder extends Builder {

    private final String inputDirectory;
    
    private final String outputDirectory;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public TattletaleBuilder(String inputDirectory, String outputDirectory) {
        this.inputDirectory = inputDirectory;
        this.outputDirectory = outputDirectory;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getInputDirectory() {
        return inputDirectory;
    }

    public String getOutputLocation() {
		return outputDirectory;
	}

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // This is where you 'build' the project.

    	listener.getLogger().println("Input directory: " + inputDirectory);
    	
    	listener.getLogger().println("Output directory: " + outputDirectory);
    	
        // This also shows how you can consult the global configuration of the builder
        if (getDescriptor().getOverrideConfig()) {
        	listener.getLogger().println("Default global config overriden.");
        	listener.getLogger().println("Tattletale jar location: \n" 
        			+ getDescriptor().getTattletaleJarLocation());
        	listener.getLogger().println("Javassist jar location: \n" 
        			+ getDescriptor().getJavassistJarLocation());
        	listener.getLogger().println("Tattletale properties location: \n" 
        			+ getDescriptor().getPropertiesLocation());
        	
        }
        
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link TattletaleBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/org/jenkinsci/plugins/TattletaleBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean overrideConfig;
        
        private String tattletaleJarLocation;
        
        private String javassistJarLocation;

		private String propertiesLocation;
		
		public DescriptorImpl() {
            super(TattletaleBuilder.class);
            load();
        }
		
        /**
         * Performs on-the-fly validation of the form field 'inputDirectory'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckInputDirectory(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a project location to be analyzed.");
            return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'outputDirectory'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckOutputDirectory(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set tattletale report directory");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Invoke Tattletale";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            overrideConfig = formData.getBoolean("overrideConfig");
            tattletaleJarLocation = formData.getString("tattletaleJarLocation");
            javassistJarLocation =  formData.getString("javassistJarLocation");
            propertiesLocation =  formData.getString("propertiesLocation");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        public void setOverrideConfig(boolean overrideConfig) {
			this.overrideConfig = overrideConfig;
		}

		public void setTattletaleJarLocation(String tattletaleJarLocation) {
			this.tattletaleJarLocation = tattletaleJarLocation;
		}

		public void setJavassistJarLocation(String javassistJarLocation) {
			this.javassistJarLocation = javassistJarLocation;
		}

		public void setPropertiesLocation(String propertiesLocation) {
			this.propertiesLocation = propertiesLocation;
		}

		/**
         * 
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getOverrideConfig() {
            return overrideConfig;
        }
        
        public String getTattletaleJarLocation() {
			return tattletaleJarLocation;
		}
        
        public String getJavassistJarLocation() {
        	return javassistJarLocation;
        }

		public String getPropertiesLocation() {
			return propertiesLocation;
		}

    }
}

