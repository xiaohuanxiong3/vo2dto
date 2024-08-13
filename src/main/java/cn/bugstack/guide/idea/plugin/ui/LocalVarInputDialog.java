package cn.bugstack.guide.idea.plugin.ui;

import cn.bugstack.guide.idea.plugin.application.IGenerateVo2Dto;
import cn.bugstack.guide.idea.plugin.domain.service.impl.GenerateVo2DtoImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.TextFieldWithAutoCompletion;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Objects;

/**
 * @Description
 * @Author Handsome Young
 * @Date 2024/8/8 22:25
 */
public class LocalVarInputDialog extends JDialog {

    // 静态自动补全输入框
    private TextFieldWithAutoCompletion<String> inputField;

    private final Project project;

    private final AnActionEvent event;

    private final List<PsiVariable> variables;

    private final List<String> suggestions;

    private static final IGenerateVo2Dto generateVo2Dto = new GenerateVo2DtoImpl();

    public LocalVarInputDialog(AnActionEvent event, List<PsiVariable> variables, List<String> suggestions) {
        super((Frame) null, false); // 设置为非模态
        this.project = event.getProject();
        this.event = event;
        this.variables = variables;
        this.suggestions = suggestions;
        setUndecorated(true);
        init();
        addWindowListener();
    }

    private void init() {
        JPanel dialogPanel = new JPanel(new BorderLayout());
        inputField = new TextFieldWithAutoCompletion<>(
                project,
                new TextFieldWithAutoCompletion.StringsCompletionProvider(suggestions, null),
                false,
                null
        );
        inputField.setPlaceholder("请输入要转换的变量名");
        dialogPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirmButton = new JButton("确认");
        confirmButton.addActionListener(e -> {
            if (!suggestions.contains(getInput())) {
                Messages.showInfoMessage(dialogPanel, "请输入有效的变量名!", "提示");
                return;
            }
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String input = getInput();
                List<PsiVariable> matchedPsiVariableList = variables.stream().filter(psiVariable -> Objects.equals(psiVariable.getName(), input)).toList();
                if (matchedPsiVariableList.size() == 1) {
                    PsiVariable matchedPsiVariable = matchedPsiVariableList.get(0);
                    ApplicationManager.getApplication().runReadAction(() -> {
                        generateVo2Dto.doGenerate(project, event.getDataContext(), (PsiVariable) event.getData(CommonDataKeys.PSI_ELEMENT),matchedPsiVariable);
                    });
                }
            });
            dispose(); // 关闭并销毁对话框
        });

        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> {
            dispose(); // 关闭并销毁对话框
        });

        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);

        dialogPanel.add(buttonPanel, BorderLayout.SOUTH);
        dialogPanel.setPreferredSize(new Dimension(200, 80));

        setContentPane(dialogPanel);
        pack();
        setLocationRelativeTo(WindowManager.getInstance().getFrame(project));
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
    }

    @Override
    public void setShape(Shape shape) {
        super.setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 40, 40));
    }

    // 获取输入内容
    public String getInput() {
        return inputField.getText();
    }

    private void addWindowListener() {
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowDeactivated(WindowEvent e) {
                // 关闭并销毁对话框
                dispose();
            }

        });
    }

}
