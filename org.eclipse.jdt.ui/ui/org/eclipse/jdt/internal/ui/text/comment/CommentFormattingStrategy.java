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

package org.eclipse.jdt.internal.ui.text.comment;

import java.util.LinkedList;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy;
import org.eclipse.jface.text.formatter.FormattingContextProperties;
import org.eclipse.jface.text.formatter.IFormattingContext;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Formatting strategy for general source code comments.
 * 
 * @since 3.0
 */
public class CommentFormattingStrategy extends ContextBasedFormattingStrategy {

	/** Documents to be formatted by this strategy */
	private final LinkedList fDocuments= new LinkedList();

	/** Partitions to be formatted by this strategy */
	private final LinkedList fPartitions= new LinkedList();

	/** Text measurement, can be <code>null</code> */
	private ITextMeasurement fTextMeasurement;

	/**
	 * The last formatted document's hash-code.
	 * @since 3.0
	 */
	private int fLastDocumentHash;
	/**
	 * The last formatted document header's hash-code.
	 * @since 3.0
	 */
	private int fLastHeaderHash;
	/**
	 * The end of the first class or interface token in the last document.
	 * @since 3.0
	 */
	private int fLastMainTokenEnd= -1;
	/**
	 * The end of the header in the last document.
	 * @since 3.0
	 */
	private int fLastDocumentsHeaderEnd;


	/**
	 * Creates a new comment formatting strategy.
	 */
	public CommentFormattingStrategy() {
		super();
	}

	/**
	 * Creates a new comment formatting strategy.
	 * 
	 * @param textMeasurement
	 *                  The text measurement
	 */
	public CommentFormattingStrategy(ITextMeasurement textMeasurement) {
		super();
		fTextMeasurement= textMeasurement;
	}

	/**
	 * @inheritDoc
	 */
	public void format() {
		super.format();
		
		final IDocument document= (IDocument) fDocuments.removeFirst();
		final TypedPosition position= (TypedPosition)fPartitions.removeFirst();
		
		if (document != null && position != null) {
			final boolean isFormmatingComments= getPreferences().get(PreferenceConstants.FORMATTER_COMMENT_FORMAT).equals(IPreferenceStore.TRUE);
			final boolean isFormattingHeader= getPreferences().get(PreferenceConstants.FORMATTER_COMMENT_FORMATHEADER).equals(IPreferenceStore.TRUE);
			
			int documentsHeaderEnd= computeHeaderEnd(document);
			
			if (isFormmatingComments && (isFormattingHeader || position.offset >= documentsHeaderEnd)) {
				
				final CommentRegion region= CommentObjectFactory.createRegion(document, position, TextUtilities.getDefaultLineDelimiter(document), getPreferences(), fTextMeasurement);
				final TextEdit edit= region.format();
				try {
					
					if (edit != null)
						edit.apply(document);
					
				} catch (MalformedTreeException exception) {
					JavaPlugin.log(exception);
				} catch (BadLocationException exception) {
					JavaPlugin.log(exception);
				}
			}
		}
	}

	/**
	 * Returns the end offset for the document's header.
	 * 
	 * @param document the document
	 * @return the header's end offset
	 * @since 3.0
	 */
	private int computeHeaderEnd(IDocument document) {
		if (document == null)
			return -1;
		
		try {
			if (fLastMainTokenEnd >= 0 && document.hashCode() == fLastDocumentHash && fLastMainTokenEnd < document.getLength() && document.get(0, fLastMainTokenEnd).hashCode() == fLastHeaderHash)
				return fLastDocumentsHeaderEnd;
		} catch (BadLocationException e) {
			// should not happen -> recompute
		}
		
		IScanner scanner= ToolFactory.createScanner(true, false, false, false);
		scanner.setSource(document.get().toCharArray());

		try {
			int offset= -1;
			boolean foundComment= false;
			int terminal= scanner.getNextToken();
			while (terminal != ITerminalSymbols.TokenNameEOF && !(terminal == ITerminalSymbols.TokenNameclass || terminal == ITerminalSymbols.TokenNameinterface || (foundComment && (terminal == ITerminalSymbols.TokenNameimport || terminal == ITerminalSymbols.TokenNamepackage)))) {
				
				if (terminal == ITerminalSymbols.TokenNameCOMMENT_JAVADOC)
					offset= scanner.getCurrentTokenStartPosition();
				
				foundComment= terminal == ITerminalSymbols.TokenNameCOMMENT_JAVADOC || terminal == ITerminalSymbols.TokenNameCOMMENT_BLOCK;
				
				terminal= scanner.getNextToken();
			}
			
			int mainTokenEnd= scanner.getCurrentTokenEndPosition();
			if (terminal != ITerminalSymbols.TokenNameEOF) {
				mainTokenEnd++;
				if (offset == -1 || (foundComment && (terminal == ITerminalSymbols.TokenNameimport || terminal == ITerminalSymbols.TokenNamepackage)))
					offset= scanner.getCurrentTokenStartPosition();
			} else
				offset= -1;
			
			try {
				fLastHeaderHash= document.get(0, mainTokenEnd).hashCode();
			} catch (BadLocationException e) {
				// should not happen -> recompute next time
				mainTokenEnd= -1;
			}
			
			fLastDocumentHash= document.hashCode();
			fLastMainTokenEnd= mainTokenEnd;
			fLastDocumentsHeaderEnd= offset;
			return offset;
			
		} catch (InvalidInputException ex) {
			// enable formatting
			return -1;
		}
	}

	/*
	 * @see org.eclipse.jface.text.formatter.IFormattingStrategyExtension#formatterStarts(org.eclipse.jface.text.formatter.IFormattingContext)
	 */
	public void formatterStarts(IFormattingContext context) {
		super.formatterStarts(context);

		fPartitions.addLast(context.getProperty(FormattingContextProperties.CONTEXT_PARTITION));
		fDocuments.addLast(context.getProperty(FormattingContextProperties.CONTEXT_MEDIUM));
	}

	/*
	 * @see org.eclipse.jface.text.formatter.IFormattingStrategyExtension#formatterStops()
	 */
	public void formatterStops() {
		fPartitions.clear();
		fDocuments.clear();
		
		super.formatterStops();
	}
}
