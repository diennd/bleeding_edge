/*
 * Copyright (c) 2013, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dart.tools.ui.internal.text.editor;

import com.google.common.collect.Lists;
import com.google.dart.engine.ast.ASTNode;
import com.google.dart.engine.ast.Expression;
import com.google.dart.engine.ast.MethodDeclaration;
import com.google.dart.engine.ast.visitor.ElementLocator;
import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.ExecutableElement;
import com.google.dart.engine.element.ParameterElement;
import com.google.dart.engine.element.PropertyAccessorElement;
import com.google.dart.engine.type.Type;
import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.core.utilities.dartdoc.DartDocUtilities;
import com.google.dart.tools.ui.internal.actions.NewSelectionConverter;
import com.google.dart.tools.ui.internal.util.GridDataFactory;
import com.google.dart.tools.ui.internal.util.GridLayoutFactory;
import com.google.dart.tools.ui.text.DartSourceViewerConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.Iterator;
import java.util.List;

public class DartHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {
  private static class DartInformationControl extends AbstractInformationControl implements
      IInformationControlExtension2 {
    private static final Point SIZE_CONSTRAINTS = new Point(10000, 10000);

    private static void setGridVisible(Control control, boolean visible) {
      GridDataFactory.modify(control).exclude(!visible);
      control.setVisible(visible);
      control.getParent().layout();
    }

    private static void setGridVisible(HtmlSection section, boolean visible) {
      setGridVisible(section.section, visible);
    }

    private static void setGridVisible(TextSection section, boolean visible) {
      setGridVisible(section.section, visible);
    }

    private HoverInfo hoverInfo;

    private ScrolledForm form;
    private HtmlSection docSection;
    private TextSection problemsSection;
    private TextSection staticTypeSection;
    private TextSection propagatedTypeSection;
    private TextSection parameterSection;

    public DartInformationControl(Shell parentShell) {
      super(parentShell, false);
      create();
    }

    @Override
    public Point computeSizeConstraints(int widthInChars, int heightInChars) {
      return SIZE_CONSTRAINTS;
    }

    @Override
    public Point computeSizeHint() {
      // Shell was already packed and has the required size.
      return getShell().getSize();
    }

    @Override
    public IInformationControlCreator getInformationPresenterControlCreator() {
      return new DartInformationControlCreator();
    }

    @Override
    public boolean hasContents() {
      return hoverInfo != null;
    }

    @Override
    public void setInput(Object input) {
      hoverInfo = null;
      // Hide all sections.
      setGridVisible(docSection, false);
      setGridVisible(problemsSection, false);
      setGridVisible(staticTypeSection, false);
      setGridVisible(propagatedTypeSection, false);
      setGridVisible(parameterSection, false);
      // Prepare input.
      if (!(input instanceof HoverInfo)) {
        return;
      }
      hoverInfo = (HoverInfo) input;
      ASTNode node = hoverInfo.node;
      Element element = hoverInfo.element;
      // Element
      if (element != null) {
        // show variable, if synthetic accessor
        if (element instanceof PropertyAccessorElement) {
          PropertyAccessorElement accessor = (PropertyAccessorElement) element;
          if (accessor.isSynthetic()) {
            element = accessor.getVariable();
          }
        }
        // use the Element as a title
        {
          String title = element.toString();
          title = StringUtils.abbreviateMiddle(title, " ... ", 80);
          form.setText(title);
        }
        // Dart Doc
        {
          String dartDoc = DartDocUtilities.getDartDocAsHtml(element);
          if (dartDoc != null) {
            setGridVisible(docSection, true);
            docSection.setHtml(dartDoc);
          }
        }
      }
      // types
      if (node instanceof Expression) {
        Expression expression = (Expression) node;
        // show node if no Element
        if (element == null) {
          String nodeTitle = node.toSource();
          nodeTitle = StringUtils.abbreviate(nodeTitle, 80);
          form.setText(nodeTitle);
        }
        // parameter
        {
          ASTNode n = expression;
          while (n != null) {
            if (n instanceof Expression) {
              ParameterElement parameterElement = ((Expression) n).getBestParameterElement();
              if (parameterElement != null) {
                setGridVisible(parameterSection, true);
                parameterSection.setText(DartDocUtilities.getTextSummary(null, parameterElement));
                break;
              }
            }
            n = n.getParent();
          }
        }
        // static type
        Type staticType = expression.getStaticType();
        if (staticType != null && element == null) {
          setGridVisible(staticTypeSection, true);
          staticTypeSection.setText(staticType.getDisplayName());
        }
        // propagated type
        if (!(element instanceof ExecutableElement)) {
          Type propagatedType = expression.getPropagatedType();
          if (propagatedType != null && !propagatedType.equals(staticType)) {
            setGridVisible(propagatedTypeSection, true);
            propagatedTypeSection.setText(propagatedType.getDisplayName());
          }
        }
      }
      // Annotations.
      {
        List<Annotation> annotations = hoverInfo.annotations;
        int size = annotations.size();
        if (size != 0) {
          // prepare annotations text
          StringBuilder buffer = new StringBuilder();
          if (size > 1) {
            buffer.append("Multiple annotations at this position:");
          }
          for (Annotation annotation : annotations) {
            if (buffer.length() != 0) {
              buffer.append("\n");
            }
            if (size > 1) {
              buffer.append("\t- ");
            }
            buffer.append(annotation.getText());
          }
          // show annotations as text
          setGridVisible(problemsSection, true);
          problemsSection.setText(buffer.toString());
        }
      }
      // Layout and pack.
      Shell shell = getShell();
      shell.layout(true, true);
      shell.pack();
      shell.layout(true, true);
      shell.pack();
    }

    @Override
    protected void createContent(Composite parent) {
      form = formToolkit.createScrolledForm(parent);
      formToolkit.decorateFormHeading(form.getForm());

      Composite body = form.getBody();
      GridLayoutFactory.create(body);

      problemsSection = new TextSection(body, "Problems");
      docSection = new HtmlSection(body, "Documentation");
      staticTypeSection = new TextSection(body, "Static type");
      propagatedTypeSection = new TextSection(body, "Propagated type");
      parameterSection = new TextSection(body, "Parameter");
    }
  }

  private static class DartInformationControlCreator extends
      AbstractReusableInformationControlCreator {
    @Override
    protected IInformationControl doCreateInformationControl(Shell parent) {
      return new DartInformationControl(parent);
    }
  }

  private static class HoverInfo {
    ASTNode node;
    Element element;
    List<Annotation> annotations;

    public HoverInfo(ASTNode node, Element element, List<Annotation> annotations) {
      this.node = node;
      this.element = element;
      this.annotations = annotations;
    }
  }

  private static class HtmlSection {
    private final Section section;
    private Browser browser;
    private Text textWidget;

    public HtmlSection(Composite parent, String title) {
      this.section = formToolkit.createSection(parent, Section.TITLE_BAR);
      GridDataFactory.create(section).grab().fill();
      section.setText(title);
      if (DartCore.isLinux()) {
        textWidget = new Text(section, SWT.BORDER | SWT.V_SCROLL);
        formToolkit.adapt(textWidget, false, false);
        section.setClient(textWidget);
      } else {
        // create Composite to draw flat border
        Composite body = formToolkit.createComposite(section);
        GridLayoutFactory.create(body).margins(2);
        section.setClient(body);
        // create Browser
        browser = new Browser(body, SWT.NONE);
        GridDataFactory.create(browser).grab().fill();
        // configure flat border
        browser.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TEXT_BORDER);
        formToolkit.paintBordersFor(body);
      }
    }

    public void setHtml(String html) {
      if (DartCore.isLinux()) {
        textWidget.setText(html);
        textWidget.setSelection(0);
      } else {
        browser.setText(html);
      }
      // tweak Browser size
      int lineLength = Math.min(html.length(), 85);
      int numLines = estimateNumLines(html, lineLength);
      numLines += 3; // header
      numLines += 1; // footer
      numLines = Math.min(numLines, 15);
      GridDataFactory.create(section).hintChars(lineLength + 5, numLines).grab().fill();
    }
  }

  private static class TextSection {
    private final Section section;
    private final Text textWidget;

    public TextSection(Composite parent, String title) {
      this.section = formToolkit.createSection(parent, Section.TITLE_BAR);
      GridDataFactory.create(section).grabHorizontal().fill();
      section.setText(title);
      textWidget = new Text(section, SWT.NONE);
      formToolkit.adapt(textWidget, false, false);
      section.setClient(textWidget);
    }

    public void setText(String text) {
      textWidget.setText(text);
      textWidget.setSelection(0);
    }
  }

  private static final List<ITextHover> hoverContributors = Lists.newArrayList();

  private static final FormToolkit formToolkit = new FormToolkit(Display.getDefault());

  /**
   * Register a {@link ITextHover} tooltip contributor.
   */
  public static void addContributer(ITextHover hoverContributor) {
    hoverContributors.add(hoverContributor);
  }

  private static int estimateNumLines(String text, int width) {
    int num = text.length() / width;
    num += StringUtils.countMatches(text, "<br");
    return num;
  }

  private final ISourceViewer viewer;
  private final DartSourceViewerConfiguration viewerConfiguration;
  private CompilationUnitEditor editor;
  private IInformationControlCreator informationControlCreator;
  private ITextHover lastReturnedHover;

  public DartHover(ITextEditor editor, ISourceViewer viewer,
      DartSourceViewerConfiguration viewerConfiguration) {
    this.viewer = viewer;
    this.viewerConfiguration = viewerConfiguration;
    if (editor instanceof CompilationUnitEditor) {
      this.editor = (CompilationUnitEditor) editor;
    }
  }

  @Override
  public IInformationControlCreator getHoverControlCreator() {
    if (lastReturnedHover instanceof ITextHoverExtension) {
      return ((ITextHoverExtension) lastReturnedHover).getHoverControlCreator();
    }
    if (informationControlCreator == null) {
      informationControlCreator = new DartInformationControlCreator();
    }
    return informationControlCreator;
  }

  @Override
  public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
    lastReturnedHover = null;
    return null;
  }

  @Override
  public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
    lastReturnedHover = null;
    // Check through the contributed hover providers.
    for (ITextHover hoverContributer : hoverContributors) {
      if (hoverContributer instanceof ITextHoverExtension2) {
        Object hoverInfo = ((ITextHoverExtension2) hoverContributer).getHoverInfo2(
            textViewer,
            hoverRegion);
        if (hoverInfo != null) {
          lastReturnedHover = hoverContributer;
          return hoverInfo;
        }
      }
    }
    // Editor based hover.
    if (editor != null) {
      ASTNode node = NewSelectionConverter.getNodeAtOffset(editor, hoverRegion.getOffset());
      if (node instanceof MethodDeclaration) {
        MethodDeclaration method = (MethodDeclaration) node;
        node = method.getName();
      }
      if (node instanceof Expression) {
        Element element = ElementLocator.locate(node);
        List<Annotation> annotations = getAnnotations(hoverRegion);
        return new HoverInfo(node, element, annotations);
      }
    }
    return null;
  }

  @Override
  public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
    return findWord(textViewer.getDocument(), offset);
  }

  private IRegion findWord(IDocument document, int offset) {
    int start = -2;
    int end = -1;

    try {

      int pos = offset;
      char c;

      while (pos >= 0) {
        c = document.getChar(pos);
        if (!Character.isUnicodeIdentifierPart(c)) {
          break;
        }
        --pos;
      }

      start = pos;

      pos = offset;
      int length = document.getLength();

      while (pos < length) {
        c = document.getChar(pos);
        if (!Character.isUnicodeIdentifierPart(c)) {
          break;
        }
        ++pos;
      }

      end = pos;

    } catch (BadLocationException x) {
    }

    if (start >= -1 && end > -1) {
      if (start == offset && end == offset) {
        return new Region(offset, 0);
      } else if (start == offset) {
        return new Region(start, end - start);
      } else {
        return new Region(start + 1, end - start - 1);
      }
    }

    return null;
  }

  private IAnnotationModel getAnnotationModel() {
    if (viewer instanceof ISourceViewerExtension2) {
      ISourceViewerExtension2 extension = (ISourceViewerExtension2) viewer;
      return extension.getVisualAnnotationModel();
    }
    return viewer.getAnnotationModel();
  }

  private List<Annotation> getAnnotations(IRegion region) {
    List<Annotation> annotations = Lists.newArrayList();
    IAnnotationModel model = getAnnotationModel();
    if (model != null) {
      @SuppressWarnings("unchecked")
      Iterator<Annotation> iter = model.getAnnotationIterator();
      while (iter.hasNext()) {
        Annotation annotation = iter.next();
        if (viewerConfiguration.isShownInText(annotation)) {
          Position p = model.getPosition(annotation);
          if (p != null && p.overlapsWith(region.getOffset(), region.getLength())) {
            String msg = annotation.getText();
            if (msg != null && msg.trim().length() > 0) {
              annotations.add(annotation);
            }
          }
        }
      }
    }
    return annotations;
  }

}
