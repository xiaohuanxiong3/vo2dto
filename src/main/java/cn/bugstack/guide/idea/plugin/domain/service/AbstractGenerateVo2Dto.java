package cn.bugstack.guide.idea.plugin.domain.service;

import cn.bugstack.guide.idea.plugin.application.IGenerateVo2Dto;
import cn.bugstack.guide.idea.plugin.domain.model.GenerateContext;
import cn.bugstack.guide.idea.plugin.domain.model.GetObjConfigDO;
import cn.bugstack.guide.idea.plugin.domain.model.MethodVO;
import cn.bugstack.guide.idea.plugin.domain.model.SetObjConfigDO;
import cn.bugstack.guide.idea.plugin.infrastructure.DataSetting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractGenerateVo2Dto implements IGenerateVo2Dto {

    protected final String setRegex = "set(\\w+)";
    protected final String getRegex = "get(\\w+)";

    @Override
    public void doGenerate(Project project, DataContext dataContext, PsiVariable toPsiVariable, PsiVariable fromPsiVariable) {
        // 1. 获取上下文
        GenerateContext generateContext = this.getGenerateContext(project, dataContext, toPsiVariable);

        // 2. 获取要设置属性的对象的 set 方法集合
        SetObjConfigDO setObjConfigDO = this.getSetObjConfigDO(generateContext, toPsiVariable);

        // 3. 获取要获取属性的对象的 get 方法集合 【类名和属性名透传过来】
        GetObjConfigDO getObjConfigDO = getGetConfigDOByPsiVariable(generateContext, fromPsiVariable);

        // 4. 弹框选择，织入代码。分为弹窗提醒和非弹窗提醒
        DataSetting.DataState state = DataSetting.getInstance(project).getState();
        assert state != null;
        if ("hide".equals(state.getConfigRadio())) {
            this.weavingSetGetCode(generateContext, setObjConfigDO, getObjConfigDO);
        } else {
            ApplicationManager.getApplication().invokeLater(() -> {
                this.convertSetting(project, generateContext, setObjConfigDO, getObjConfigDO);
            });
        }
    }

    protected abstract GenerateContext getGenerateContext(Project project, DataContext dataContext, PsiElement psiElement);

    protected abstract SetObjConfigDO getSetObjConfigDO(GenerateContext generateContext, PsiVariable variable);

    protected abstract GetObjConfigDO getGetConfigDOByPsiVariable(GenerateContext generateContext, PsiVariable variable);

    protected abstract void convertSetting(Project project, GenerateContext generateContext, SetObjConfigDO setObjConfigDO, GetObjConfigDO getObjConfigDO);

    protected abstract void weavingSetGetCode(GenerateContext generateContext, SetObjConfigDO setObjConfigDO, GetObjConfigDO getObjConfigDO);

    protected List<PsiClass> getPsiClassLinkList(PsiClass psiClass) {
        List<PsiClass> psiClassList = new ArrayList<>();
        PsiClass currentClass = psiClass;
        while (null != currentClass && !"Object".equals(currentClass.getName())) {
            psiClassList.add(currentClass);
            currentClass = currentClass.getSuperClass();
        }
        Collections.reverse(psiClassList);
        return psiClassList;
    }

    protected MethodVO getMethods(PsiClass psiClass, String regex, String typeStr) {
        PsiMethod[] psiMethods = psiClass.getMethods();
        List<String> fieldNameList = new ArrayList<>();
        List<String> methodNameList = new ArrayList<>();

        PsiField[] psiFields = psiClass.getFields();
        for (PsiField field : psiFields) {
            fieldNameList.add(field.getName());
        }

        // 判断使用了 lombok，需要补全生成 get、set
        if (isUsedLombokData(psiClass)) {
            Pattern p = Pattern.compile("static.*?final|final.*?static");
            PsiField[] fields = psiClass.getFields();
            for (PsiField psiField : fields) {
                PsiElement context = psiField.getNameIdentifier().getContext();
                if (null == context) continue;
                String fieldVal = context.getText();
                // serialVersionUID 判断
                if (fieldVal.contains("serialVersionUID")) {
                    continue;
                }
                // static final 常量判断过滤
                Matcher matcher = p.matcher(fieldVal);
                if (matcher.find()) {
                    continue;
                }
                String name = psiField.getNameIdentifier().getText();
                methodNameList.add(typeStr + name.substring(0, 1).toUpperCase() + name.substring(1));
                fieldNameList.add(name);
            }

            for (PsiMethod method : psiMethods) {
                String methodName = method.getName();
                if (Pattern.matches(regex, methodName) && !methodNameList.contains(methodName)) {
                    methodNameList.add(methodName);
                }
            }

            return new MethodVO(fieldNameList, methodNameList);
        }


        // 正常创建的get、set，直接获取即可
        for (PsiMethod method : psiMethods) {
            String methodName = method.getName();
            if (Pattern.matches(regex, methodName)) {
                methodNameList.add(methodName);
            }
        }

        return new MethodVO(fieldNameList, methodNameList);
    }

    protected boolean isUsedLombokData(PsiClass psiClass) {
        return null != psiClass.getAnnotation("lombok.Data");
    }

    protected boolean isUsedLombokBuilder(PsiClass psiClass) {
        return null != psiClass.getAnnotation("lombok.Builder");
    }

    protected List<String> getImportList(String docText) {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("import(.*?);");
        Matcher m = p.matcher(docText);
        while (m.find()) {
            String val = m.group(1).replaceAll(" ", "");
            list.add(val.substring(0, val.lastIndexOf(".")));
        }
        return list;
    }

}
