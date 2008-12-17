package automaticworkingsets.actions;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListSelectionDialog;

/**
 * This action computes working sets based on several criterion.
 * 
 * <ul>
 * <li>Project nature</li>
 * <li>Open vs. Closed</li>
 * <li>Non Shared</li>
 * <li><b>SVN:</b> Projects with common parent folder in SVN repository</li>
 * </ul>
 * 
 * @author Sandip Chitale
 * 
 */
public class CreateComputedWorkingSets implements
		IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow window;

	public void dispose() {
	}

	public void init(IWorkbenchWindow window) {
		this.window = window;
	}

	private static enum PROJECT_TYPES {
		Plugin_and_fragment, Feature, Update_site, Java, Open, Closed, Non_Shared, Svn,
	};

	public void run(IAction action) {

		// Select project types
		ListSelectionDialog workingSetsToCreate = new ListSelectionDialog(
				window.getShell(), PROJECT_TYPES.values(),
				new ArrayContentProvider(), new LabelProvider() {
					@Override
					public String getText(Object element) {
						return element.toString().replaceAll("_", " ")
								+ " Projects";
					}
				}, "Select Project Types for Computed Working Set Creation");
		workingSetsToCreate.setInitialSelections(PROJECT_TYPES.values());
		workingSetsToCreate.setTitle("Select Project Types");
		if (workingSetsToCreate.open() != Window.OK) {
			// User canceled
			return;
		}

		Object[] result = workingSetsToCreate.getResult();
		if (result.length == 0) {
			// User did not select any
			return;
		}

		// Remember user selection
		@SuppressWarnings("unchecked")
		Set set = new HashSet(Arrays.asList(result));

		// Get projects
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();
		
		List<IProject> pluginAndFragmentProjects = new LinkedList<IProject>();
		List<IProject> featureProjects = new LinkedList<IProject>();
		List<IProject> updateSiteProjects = new LinkedList<IProject>();
		List<IProject> javaProjects = new LinkedList<IProject>();
		List<IProject> openProjects = new LinkedList<IProject>();
		List<IProject> closedProjects = new LinkedList<IProject>();
		List<IProject> nonSharedProjects = new LinkedList<IProject>();

		Map<String, List<IProject>> svnProjects = new HashMap<String, List<IProject>>();

		for (IProject project : projects) {
			if (set.contains(PROJECT_TYPES.Plugin_and_fragment)) {
				// plugins and fragment projects
				try {
					if (project.getNature("org.eclipse.pde.PluginNature") != null) {
						pluginAndFragmentProjects.add(project);
					}
				} catch (CoreException e) {
				}
			}

			if (set.contains(PROJECT_TYPES.Feature)) {
				// feature projects
				try {
					if (project.getNature("org.eclipse.pde.FeatureNature") != null) {
						featureProjects.add(project);
					}
				} catch (CoreException e) {
				}
			}
			// update site projects
			if (set.contains(PROJECT_TYPES.Update_site)) {
				try {
					if (project.getNature("org.eclipse.pde.UpdateSiteNature") != null) {
						updateSiteProjects.add(project);
					}
				} catch (CoreException e) {
				}
			}

			// Java projects
			if (set.contains(PROJECT_TYPES.Java)) {
				try {
					if (project.getNature("org.eclipse.jdt.core.javanature") != null) {
						javaProjects.add(project);
					}
				} catch (CoreException e) {
				}
			}

			if (project.isAccessible()) {
				if (set.contains(PROJECT_TYPES.Open)) {
					openProjects.add(project);
				}
				boolean shared = RepositoryProvider.isShared(project);
				if (shared) {
					RepositoryProvider provider = RepositoryProvider
							.getProvider(project);
					if (provider != null) {
						if (set.contains(PROJECT_TYPES.Svn)) {
							if (provider
									.getClass()
									.getName()
									.equals(
											"org.tigris.subversion.subclipse.core.SVNTeamProvider")) {
								try {
									Method getSVNWorkspaceRootMethod = provider
											.getClass().getMethod(
													"getSVNWorkspaceRoot");
									Object SVNWorkspaceRoot = getSVNWorkspaceRootMethod
											.invoke(provider);
									if (SVNWorkspaceRoot != null) {
										Method getBaseResourceForMethod = SVNWorkspaceRoot
												.getClass().getMethod(
														"getBaseResourceFor",
														IResource.class);
										Object baseResource = getBaseResourceForMethod
												.invoke(null, project);
										if (baseResource != null) {
											Method getUrlMethod = baseResource
													.getClass().getMethod(
															"getUrl");
											String url = getUrlMethod.invoke(
													baseResource).toString();
											if (url != null) {
												int lastIndexOfSlash = url
														.lastIndexOf("/");
												if (lastIndexOfSlash != -1) {
													url = url.substring(0,
															lastIndexOfSlash);
												}

												List<IProject> list = svnProjects
														.get(url);
												if (list == null) {
													list = new LinkedList<IProject>();
													svnProjects.put(url, list);
												}
												list.add(project);
											}
										}
									}
								} catch (Exception e) {
								}
							}
						}
					}
				} else if (set.contains(PROJECT_TYPES.Non_Shared)) {
					nonSharedProjects.add(project);
				}
			} else {
				if (set.contains(PROJECT_TYPES.Closed)) {
					closedProjects.add(project);
				}
			}
		}

		IWorkingSetManager workingSetManager = PlatformUI.getWorkbench()
				.getWorkingSetManager();

		if (set.contains(PROJECT_TYPES.Plugin_and_fragment)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Plugin_and_fragment.toString().replaceAll("_", " "),
					pluginAndFragmentProjects);
		}
		if (set.contains(PROJECT_TYPES.Feature)) {
			createWorkingSet(workingSetManager,  PROJECT_TYPES.Feature.toString().replaceAll("_", " "), featureProjects);
		}
		if (set.contains(PROJECT_TYPES.Update_site)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Update_site.toString().replaceAll("_", " "),
					updateSiteProjects);
		}
		if (set.contains(PROJECT_TYPES.Java)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Java.toString().replaceAll("_", " "), javaProjects);
		}
		if (set.contains(PROJECT_TYPES.Open)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Open.toString().replaceAll("_", " "), openProjects);
		}
		if (set.contains(PROJECT_TYPES.Closed)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Closed.toString().replaceAll("_", " "), closedProjects);
		}
		if (set.contains(PROJECT_TYPES.Non_Shared)) {
			createWorkingSet(workingSetManager, PROJECT_TYPES.Non_Shared.toString().replaceAll("_", " "), nonSharedProjects);
		}

		if (set.contains(PROJECT_TYPES.Svn)) {
			Set<String> keySet = svnProjects.keySet();
			for (String url : keySet) {
				createWorkingSet(workingSetManager, "SVN [" + url + "]",
						svnProjects.get(url));
			}
		}
	}

	private static void createWorkingSet(IWorkingSetManager workingSetManager,
		String workingSetName, List<IProject> projects) {
		workingSetName += " Projects [Computed]";
		IWorkingSet workingSet = workingSetManager
				.getWorkingSet(workingSetName);
		if (workingSet != null) {
			workingSetManager.removeWorkingSet(workingSet);
		}
		if (projects.size() > 1) {
			workingSet = workingSetManager.createWorkingSet(workingSetName,
					projects.toArray(new IAdaptable[projects.size()]));
			workingSetManager.addWorkingSet(workingSet);
		}
	}

	public void selectionChanged(IAction action, ISelection selection) {
	}

}
