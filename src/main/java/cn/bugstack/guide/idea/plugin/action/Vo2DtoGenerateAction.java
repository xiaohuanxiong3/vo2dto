package cn.bugstack.guide.idea.plugin.action;

import cn.bugstack.guide.idea.plugin.ui.ActionXSourcePosition;
import cn.bugstack.guide.idea.plugin.ui.SimpleAutoCompletionEditor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ResolveState;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Vo2DtoGenerateAction extends AnAction {

    private static final Logger log = LoggerFactory.getLogger(Vo2DtoGenerateAction.class);

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        try {
            // 获取当前项目、编辑器和文件
            Project project = e.getProject();
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            PsiFile psiFile = ReadAction.compute((ThrowableComputable<PsiFile, Throwable>) () -> e.getData(CommonDataKeys.PSI_FILE));
            // 检查是否为Java文件
            boolean isJavaFile = (psiFile instanceof PsiJavaFile);

            // 设置Action的可见性
            e.getPresentation().setEnabledAndVisible(project != null && editor != null && isJavaFile);
        } catch (Throwable ex) {
            log.info("exception caught when get PsiFile : {}", ex.getMessage());
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        try {
            Project project = event.getProject();
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            PsiElement psiElement = event.getData(LangDataKeys.PSI_ELEMENT);
            if (project == null || editor == null) {
                return;
            }
            // IDEA 似乎会帮我们将 局部变量名对应PsiIdentifier及其前后的空格识别为 PsiLocalVariable
            if (!(psiElement instanceof PsiLocalVariable)) {
                Messages.showErrorDialog(project, "请将光标移动到局部变量变量名上后，再进行vo2dto操作", "错误提示");
                return;
            }
//            List<PsiVariable> variables = getAccessibleVariables(psiElement);
//            List<String> suggestions = variables.stream().map(PsiNamedElement::getName).toList();
//            LocalVarInputDialog dialog = new LocalVarInputDialog(event, variables, suggestions);
//            ApplicationManager.getApplication().invokeLater(() -> {
//                dialog.setVisible(true);
//            });
//            SimpleAutoCompletionEditor simpleAutoCompletionEditor = new SimpleAutoCompletionEditor(project, null);
            ActionXSourcePosition sourcePosition = new ActionXSourcePosition(editor.getCaretModel().getLogicalPosition().line + 1, editor.getCaretModel().getOffset(), editor.getVirtualFile());
            SimpleAutoCompletionEditor simpleAutoCompletionEditor = new SimpleAutoCompletionEditor(project, event.getDataContext(), sourcePosition);
            ApplicationManager.getApplication().invokeLater(simpleAutoCompletionEditor::show);

        } catch (Exception e) {
            log.error("caught error in vo2dto action", e);
        }
    }


    /**
     * 判断元素是否是局部变量变量名（标识符之前或之后的空格也算）
     * @param psiElement 要判断的元素
     * @return 是否是局部变量变量名及其之前或之后的空格
     */
    private boolean isPsiLocalVariable(PsiElement psiElement) {
        return (psiElement instanceof PsiIdentifier
                || (psiElement instanceof PsiWhiteSpace && psiElement.getPrevSibling() instanceof PsiIdentifier)
                || (psiElement instanceof PsiWhiteSpace && psiElement.getNextSibling() instanceof PsiIdentifier) ) &&
                psiElement.getParent() instanceof PsiLocalVariable;
    }

    // 获取变量对应的全限定类名
    // variables.get(0).getType().getCanonicalText()
    private List<PsiVariable> getAccessibleVariables(PsiElement position) {
        List<PsiVariable> variables = new ArrayList<>();
        PsiElement context = position;

        while (context != null) {
            if (context instanceof PsiMethod) {
                collectMethodVariables((PsiMethod) context, variables);
            } else if (context instanceof PsiClass) {
                collectClassVariables((PsiClass) context, variables);
            } else if (context instanceof PsiCodeBlock) {
                collectLocalVariables((PsiCodeBlock) context, position, variables);
            }
            context = context.getContext();
        }

        return variables;
    }

    private void collectMethodVariables(PsiMethod method, List<PsiVariable> variables) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            variables.add(parameter);
        }
    }

    private void collectClassVariables(PsiClass psiClass, List<PsiVariable> variables) {
        for (PsiField field : psiClass.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.PRIVATE)) {
                variables.add(field);
            }
        }
    }

    private void collectLocalVariables(PsiCodeBlock codeBlock, PsiElement position, List<PsiVariable> variables) {
        PsiScopeProcessor processor = new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
                    PsiVariable variable = (PsiVariable) element;
                    variables.add((PsiVariable) element);
                }
                return true;
            }
        };

        PsiTreeUtil.treeWalkUp(processor, position, codeBlock, ResolveState.initial());
    }

    public List<PsiVariable> getAccessibleVariablesAtElement(PsiElement element) throws AnalysisCanceledException {
        PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        if (codeBlock == null) return Collections.emptyList();

        ControlFlow flow = ControlFlowFactory.getInstance(element.getProject()).getControlFlow(codeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
//        int offset = ControlFlowUtil.getStartOffset(flow, element);
//        if (offset == -1) return Collections.emptyList();

        return ControlFlowUtil.getUsedVariables(flow, 0, flow.getInstructions().size());
    }

}
