package cn.bugstack.guide.idea.plugin.ui;

import cn.bugstack.guide.idea.plugin.application.IGenerateVo2Dto;
import cn.bugstack.guide.idea.plugin.domain.service.impl.GenerateVo2DtoImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.ComboboxEditorTextField;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Font;

/**
 * @Description
 * @Author Handsome Young
 * @Date 2024/8/16 15:08
 */
public class SimpleAutoCompletionEditor extends DialogWrapper {

    private final Project myProject;

    private final EditorTextField myTextFiled;

    // 与 XDebuggerEvaluationDialog 一致，暂时使用 XSourcePosition，SourcePosition暂时不用
    private XSourcePosition mySourcePosition;

    private final PsiElementFactory myElementFactory;

    private PsiElement myContext;

    private final DataContext myDataContext;

    private static final IGenerateVo2Dto generateVo2Dto = new GenerateVo2DtoImpl();

    private final Logger logger = LoggerFactory.getLogger(SimpleAutoCompletionEditor.class);

    public SimpleAutoCompletionEditor(Project project, DataContext dataContext, XSourcePosition sourcePosition) {
        super(project, false);
        // 设置为非模态
        this.setModal(false);
        this.setTitle("请输入表示变量(从该变量获取属性)的表达式");
        this.myProject = project;
        this.myTextFiled = new ComboboxEditorTextField((Document)null, project, JavaFileType.INSTANCE);
        this.myTextFiled.addSettingsProvider(this::prepareEditor);
        this.mySourcePosition = sourcePosition;
        initDocument();
        this.myElementFactory = JavaPsiFacade.getElementFactory(myProject);
        this.myDataContext = dataContext;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        BorderLayoutPanel expressionPanel = JBUI.Panels.simplePanel();
        expressionPanel.addToCenter(this.myTextFiled);
        expressionPanel.setPreferredSize(new Dimension(300, EditorUtil.getDefaultCaretWidth()));
        expressionPanel.setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBColor.BLUE, 10, 2)
        ));
        this.myTextFiled.setBorder(JBUI.Borders.empty(0, 10));
        return expressionPanel;
    }

    @Override
    public void show() {
        super.show();
        ApplicationManager.getApplication().invokeLater(this.myTextFiled::requestFocusInWindow);
    }

    @Override
    protected void doOKAction() {
        PsiExpression expression = myElementFactory.createExpressionFromText(myTextFiled.getText(), myContext);
        String expressionText = expression.getText();
        PsiType type = expression.getType();
        if (type == null || type instanceof PsiPrimitiveType) {
            Messages.showErrorDialog(myProject, "请输入有效的表达式!（不允许输入Java基本类型）", "错误提示");
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                generateVo2Dto.doGenerate(myProject, myDataContext, (PsiVariable) myDataContext.getData(CommonDataKeys.PSI_ELEMENT), type, expressionText);
            });
        });
        super.doOKAction();
    }

    private Document createDocument(Project project, XSourcePosition sourcePosition) {
        if (project == null) {
            return null;
        }
        // 这里上下文其实可以直接从Action处拿，先跟Debug流程保持一直
//        PsiElement context = null;
        if (sourcePosition != null) {
            myContext = XDebuggerUtil.getInstance().findContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project, false);
        }

        PsiFile codeFragment;
        try {
            codeFragment = JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment("", myContext, (PsiType)null, true);
        } catch (Exception e){
            logger.error("exception caught when create codeFragment", e);
            return null;
        }

        Document document = codeFragment.getViewProvider().getDocument();

        assert document != null;

        return document;
    }

    private void initDocument() {
        Document document = createDocument(this.myProject, this.mySourcePosition);
        this.myTextFiled.setNewDocumentAndFileType(JavaFileType.INSTANCE, document);
    }

    private void updateSourcePosition(XSourcePosition sourcePosition) {
        this.mySourcePosition = sourcePosition;
        initDocument();
    }

    private void prepareEditor(EditorEx editor) {
        Font font = EditorUtil.getEditorFont();
        editor.getColorsScheme().setEditorFontName(font.getFontName());
        editor.getColorsScheme().setEditorFontSize(font.getSize());
        editor.getSettings().setLineCursorWidth(EditorUtil.getDefaultCaretWidth());
    }

}
