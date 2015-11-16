package jreframeworker.ui;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.Scanner;

import jreframeworker.Activator;
import jreframeworker.core.JReFrameworker;
import jreframeworker.engine.utils.JarModifier;
import jreframeworker.log.Log;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

public class ExportPayloadDropperWizard extends Wizard implements IExportWizard {

	public static final String PAYLOAD_DROPPER = "dropper.jar";
	public static final String EXPORT_PAYLOAD_DROPPER = "export" + File.separatorChar + PAYLOAD_DROPPER;
	
	private SelectJReFrameworkerProjectPage page1;
	private ExportPayloadDropperPage page2;
	
	private File workspace = new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().getAbsolutePath() + File.separatorChar + ".jreframeworker");
	private File dropperJar = new File(workspace.getAbsolutePath() + File.separatorChar + EXPORT_PAYLOAD_DROPPER);
	
	public ExportPayloadDropperWizard() throws Exception {
		page1 = new SelectJReFrameworkerProjectPage("Select JReFrameworker Project");
		page2 = new ExportPayloadDropperPage("Create Payload Dropper");
		setWindowTitle("Create Payload Dropper");

		if(!dropperJar.exists()){
			dropperJar.getParentFile().mkdirs();
			URL fileURL = Activator.getContext().getBundle().getEntry(JReFrameworker.EXPORT_DIRECTORY + "/" + PAYLOAD_DROPPER);
			URL resolvedFileURL = FileLocator.toFileURL(fileURL);
			// need to use the 3-arg constructor of URI in order to properly escape file system chars
			URI resolvedURI = new URI(resolvedFileURL.getProtocol(), resolvedFileURL.getPath(), null);
			InputStream dropperJarInputStream = resolvedURI.toURL().openConnection().getInputStream();
			if(dropperJarInputStream == null){
				throw new RuntimeException("Could not locate: " + PAYLOAD_DROPPER);
			}
			Files.copy(dropperJarInputStream, dropperJar.toPath());
		}
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {}
	
	@Override
	public void addPages() {
		this.addPage(page1);
		this.addPage(page2);
	}
	
	@Override
	public boolean performFinish() {
		final File dropperFile = new File(page2.getJARPath());

		IRunnableWithProgress j = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) {
				try {
					JarModifier dropper = new JarModifier(dropperJar);
					IProject project = page1.getJReFrameworkerProject().getProject();
					
					// add config file
					File configFile = project.getFile(JReFrameworker.RUNTIMES_CONFIG).getLocation().toFile();
					dropper.add("config", Files.readAllBytes(configFile.toPath()), true);
					
					// add payloads
					Scanner scanner = new Scanner(configFile);
					while(scanner.hasNextLine()){
						String[] entry = scanner.nextLine().split(",");
						if(entry[0].equals("class")){
							String relativeClassPath = entry[1].replace(".", File.separator).replace("/class", ".class");
							File classFile = new File(project.getFile(JReFrameworker.BINARY_DIRECTORY).getLocation().toFile().getAbsolutePath()
									+ File.separatorChar + relativeClassPath);
							dropper.add("payloads/" + relativeClassPath, Files.readAllBytes(classFile.toPath()), true);
						}
					}
					scanner.close();
					
					dropper.save(dropperFile);					
				} catch (Throwable t) {
					String message = "Could not create JAR binary project. " + t.getMessage();
					int style = SWT.ICON_ERROR;
					MessageBox messageBox = new MessageBox(Display.getDefault().getActiveShell(), style);
					messageBox.setMessage(message);
					messageBox.open();
					Log.error(message, t);
				} finally {
					monitor.done();
				}
			}
		};

		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(shell);

		try {
			dialog.run(true, true, j);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

}
