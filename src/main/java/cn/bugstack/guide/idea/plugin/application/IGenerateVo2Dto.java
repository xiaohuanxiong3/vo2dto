package cn.bugstack.guide.idea.plugin.application;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;

public interface IGenerateVo2Dto {

    @Deprecated
    void doGenerate(Project project, DataContext dataContext, PsiVariable toPsiVariable, PsiVariable fromPsiVariable);

    void doGenerate(Project project, DataContext dataContext, PsiVariable toPsiVariable, PsiType fromType, String fromExpressionText);

}
