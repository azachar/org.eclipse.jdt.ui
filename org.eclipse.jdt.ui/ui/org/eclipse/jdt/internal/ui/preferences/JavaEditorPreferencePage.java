/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.preferences;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.ui.internal.dialogs.WorkbenchPreferenceDialog;
import org.eclipse.ui.internal.editors.text.CHyperLink;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.util.TabFolderLayout;

/**
 * The page for setting the editor options.
 */
public final class JavaEditorPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	
	private OverlayPreferenceStore fOverlayStore;
	
	private final IPreferenceConfigurationBlock[] fConfigurationBlocks;
	private final String[] fBlockLabels;
	
	/**
	 * Tells whether the fields are initialized.
	 * @since 3.0
	 */
	private boolean fFieldsInitialized= false;
	
	/**
	 * List of master/slave listeners when there's a dependency.
	 * 
	 * @see #createDependency(Button, String, Control)
	 * @since 3.0
	 */
	private ArrayList fMasterSlaveListeners= new ArrayList();

	
	/**
	 * Creates a new preference page.
	 */
	public JavaEditorPreferencePage() {
		setDescription(PreferencesMessages.getString("JavaEditorPreferencePage.description")); //$NON-NLS-1$
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());

		fOverlayStore= new OverlayPreferenceStore(getPreferenceStore(), new OverlayPreferenceStore.OverlayKey[0]);
		
		fConfigurationBlocks= new IPreferenceConfigurationBlock[] {
				new JavaEditorAppearanceConfigurationBlock(this, fOverlayStore),
				new SmartTypingConfigurationBlock(fOverlayStore),
				new MarkOccurrencesConfigurationBlock(fOverlayStore),
				new JavaEditorNavigationConfigurationBlock(fOverlayStore),
				new FoldingConfigurationBlock(fOverlayStore),
		};
		fBlockLabels= new String[] {
				PreferencesMessages.getString("JavaEditorPreferencePage.general"), //$NON-NLS-1$
				PreferencesMessages.getString("JavaEditorPreferencePage.typing.tabTitle"), //$NON-NLS-1$
				PreferencesMessages.getString("MarkOccurrencesConfigurationBlock.title"), //$NON-NLS-1$
				PreferencesMessages.getString("JavaEditorPreferencePage.navigationTab.title"), //$NON-NLS-1$
				PreferencesMessages.getString("JavaEditorPreferencePage.folding.title"), //$NON-NLS-1$
		};
	}
	
	protected Label createDescriptionLabel(Composite parent) {
		return null; // no description since we add a hyperlinked text
	}
	
	/*
	 * @see IWorkbenchPreferencePage#init()
	 */	
	public void init(IWorkbench workbench) {
	}

	/*
	 * @see PreferencePage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		WorkbenchHelp.setHelp(getControl(), IJavaHelpContextIds.JAVA_EDITOR_PREFERENCE_PAGE);
	}

	protected ScrolledPageContent getParentScrolledComposite(Control control) {
		Control parent= control.getParent();
		while (!(parent instanceof ScrolledPageContent) && parent != null) {
			parent= parent.getParent();
		}
		if (parent instanceof ScrolledPageContent) {
			return (ScrolledPageContent) parent;
		}
		return null;
	}

	protected ExpandableComposite createManagedStyleSection(Composite parent, String label, int nColumns) {
		final ExpandableComposite excomposite= new ExpandableComposite(parent, SWT.NONE, ExpandableComposite.TWISTIE | ExpandableComposite.CLIENT_INDENT);
		excomposite.setText(label);
		excomposite.setExpanded(false);
		excomposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, nColumns, 1));
		
		updateSectionStyle(excomposite);
		return excomposite;
	}
	
	protected void updateSectionStyle(ExpandableComposite excomposite) {
//		if (excomposite.isExpanded()) {
			excomposite.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DIALOG_FONT));
//		} else {
//			excomposite.setFont(JFaceResources.getFontRegistry().get(JFaceResources.DIALOG_FONT));
//		}
	}
	
	private Control createExpandableList(Composite parent) {
		final ScrolledPageContent content = new ScrolledPageContent(parent);
		class StyleSectionManager {
			private Set fSections= new HashSet();
			private boolean fIsBeingManaged= false;
			private ExpansionAdapter fListener= new ExpansionAdapter() {
				public void expansionStateChanged(ExpansionEvent e) {
					ExpandableComposite source= (ExpandableComposite) e.getSource();
					updateSectionStyle(source);
					if (fIsBeingManaged)
						return;
					if (e.getState()) {
						try {
							fIsBeingManaged= true;
							for (Iterator iter= fSections.iterator(); iter.hasNext();) {
								ExpandableComposite composite= (ExpandableComposite) iter.next();
								if (composite != source)
									composite.setExpanded(false);
							}
						} finally {
							fIsBeingManaged= false;
						}
					}
					
					ScrolledPageContent parentScrolledComposite= getParentScrolledComposite(source);
					if (parentScrolledComposite != null) {
						parentScrolledComposite.reflow(true);
					}
				}
			};
			public void manage(ExpandableComposite section) {
				if (section == null)
					throw new NullPointerException();
				if (fSections.add(section))
					section.addExpansionListener(fListener);
			}
		}
		StyleSectionManager mgr= new StyleSectionManager();
		int nColumns= 2;
		
		Composite body= content.getBody();
		body.setLayout(new GridLayout(nColumns, false));
		
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			ExpandableComposite excomposite= createManagedStyleSection(body, fBlockLabels[i], nColumns);
			mgr.manage(excomposite);
			Control client= fConfigurationBlocks[i].createControl(excomposite);
			excomposite.setClient(client);
		}
		
		return content;
	}
	
	private Control createTabSection(Composite parent) {
		TabFolder folder= new TabFolder(parent, SWT.TOP);
		folder.setLayout(new TabFolderLayout());
		
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			TabItem item= new TabItem(folder, SWT.NONE);
			item.setText(fBlockLabels[i]);
			Control control= fConfigurationBlocks[i].createControl(folder);
			item.setControl(control);
		}
		
		return folder;
	}

	private static void indent(Control control) {
		GridData gridData= new GridData();
		gridData.horizontalIndent= 20;
		control.setLayoutData(gridData);		
	}
	
	private void createDependency(final Button master, String masterKey, final Control slave) {
		indent(slave);
		boolean masterState= fOverlayStore.getBoolean(masterKey);
		slave.setEnabled(masterState);
		SelectionListener listener= new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				slave.setEnabled(master.getSelection());
			}

			public void widgetDefaultSelected(SelectionEvent e) {}
		};
		master.addSelectionListener(listener);
		fMasterSlaveListeners.add(listener);
	}

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		fOverlayStore.load();
		fOverlayStore.start();
		
		Composite contents= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		contents.setLayout(layout);
		
		createHeader(contents);

		Control main;
		if (true)
			main= createExpandableList(contents);
		else
			main= createTabSection(contents);
		main.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		initialize();
		applyDialogFont(contents);
		contents.layout(false);
		
		return contents;
	}
	
	void foo(int i,
			int y) {
			
	}
	
	private void createHeader(Composite contents) {
		String before= PreferencesMessages.getString("JavaEditorPreferencePage.link.before"); //$NON-NLS-1$
		String linktext= PreferencesMessages.getString("JavaEditorPreferencePage.link.linktext"); //$NON-NLS-1$
		String linktooltip= PreferencesMessages.getString("JavaEditorPreferencePage.link.linktooltip"); //$NON-NLS-1$
		String after= PreferencesMessages.getString("JavaEditorPreferencePage.link.after"); //$NON-NLS-1$
		Control description= createLinkText(contents, new Object[] {
				before, 
				new String[] {linktext, "org.eclipse.ui.preferencePages.GeneralTextEditor", linktooltip }, //$NON-NLS-1$
				after});
		GridData gridData= new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		gridData.widthHint= 150; // only expand further if anyone else requires it
		description.setLayoutData(gridData);
		
//		createHBar(contents);
		addFiller(contents);
	}

	private void addFiller(Composite composite) {
		PixelConverter pixelConverter= new PixelConverter(composite);
		
		Label filler= new Label(composite, SWT.LEFT );
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		gd.heightHint= pixelConverter.convertHeightInCharsToPixels(1) / 2;
		filler.setLayoutData(gd);
	}

	private Control createLinkText(Composite contents, Object[] tokens) {
		Composite description= new Composite(contents, SWT.NONE);
		RowLayout rowLayout= new RowLayout(SWT.HORIZONTAL);
		rowLayout.justify= false;
		rowLayout.fill= true;
		rowLayout.marginBottom= 0;
		rowLayout.marginHeight= 0;
		rowLayout.marginLeft= 0;
		rowLayout.marginRight= 0;
		rowLayout.marginTop= 0;
		rowLayout.marginWidth= 0;
		rowLayout.spacing= 0;
		description.setLayout(rowLayout);
		
		for (int i= 0; i < tokens.length; i++) {
			String text;
			if (tokens[i] instanceof String[]) {
				String[] strings= (String[]) tokens[i];
				text= strings[0];
				final String target= strings[1];
				CHyperLink link= new CHyperLink(description, SWT.NONE);
				link.setText(text);
				link.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						WorkbenchPreferenceDialog.createDialogOn(target);
					}
				});
				if (strings.length > 2)
					link.setToolTipText(strings[2]);
				continue;
			}
			
			text= (String) tokens[i];
			StringTokenizer tokenizer= new StringTokenizer(text);
			while (tokenizer.hasMoreTokens()) {
				Label label= new Label(description, SWT.NONE);
				String token= tokenizer.nextToken();
				label.setText(token + " "); //$NON-NLS-1$
			}
		}
		
		return description;
	}


	private void initialize() {
		initializeFields();
	}
	
	private void initializeFields() {
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			fConfigurationBlocks[i].initialize();
		}
		
        fFieldsInitialized= true;
        
        updateStatus(new StatusInfo()); //$NON-NLS-1$
        
	}
	
	/*
	 * @see PreferencePage#performOk()
	 */
	public boolean performOk() {
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			fConfigurationBlocks[i].performOk();
		}

		fOverlayStore.propagate();
		JavaPlugin.getDefault().savePluginPreferences();
		return true;
	}
	
	/*
	 * @see PreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		fOverlayStore.loadDefaults();
		
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			fConfigurationBlocks[i].performDefaults();
		}
		initializeFields();

		super.performDefaults();
	}
	
	/*
	 * @see DialogPage#dispose()
	 */
	public void dispose() {
		for (int i= 0; i < fConfigurationBlocks.length; i++) {
			fConfigurationBlocks[i].dispose();
		}
		
		if (fOverlayStore != null) {
			fOverlayStore.stop();
			fOverlayStore= null;
		}
		super.dispose();
	}
	
	void updateStatus(IStatus status) {
		if (!fFieldsInitialized)
			return;
		
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}
}
