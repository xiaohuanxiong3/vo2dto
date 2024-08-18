package cn.bugstack.guide.idea.plugin.domain.service.impl;

import cn.bugstack.guide.idea.plugin.domain.model.GenerateContext;
import cn.bugstack.guide.idea.plugin.domain.model.GetObjConfigDO;
import cn.bugstack.guide.idea.plugin.domain.model.MethodVO;
import cn.bugstack.guide.idea.plugin.domain.model.SetObjConfigDO;
import cn.bugstack.guide.idea.plugin.domain.service.AbstractGenerateVo2Dto;
import cn.bugstack.guide.idea.plugin.infrastructure.Utils;
import cn.bugstack.guide.idea.plugin.ui.ConvertSettingUI;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class GenerateVo2DtoImpl extends AbstractGenerateVo2Dto {

    @Override
    protected GenerateContext getGenerateContext(Project project, DataContext dataContext,PsiElement psiElement) {
        // 基础信息
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (psiElement == null) {
            psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        }
        assert editor != null;
        Document document = editor.getDocument();
        PsiFile psiFile = dataContext.getData(LangDataKeys.PSI_FILE);

        // 封装生成对象上下文
        GenerateContext generateContext = new GenerateContext();
        generateContext.setProject(project);
        generateContext.setPsiFile(psiFile);
        generateContext.setDataContext(dataContext);
        generateContext.setEditor(editor);
        generateContext.setPsiElement(psiElement);
        generateContext.setOffset(editor.getCaretModel().getOffset());
        generateContext.setDocument(document);
        generateContext.setLineNumber(document.getLineNumber(generateContext.getOffset()));
        generateContext.setStartOffset(document.getLineStartOffset(generateContext.getLineNumber()));
        generateContext.setEditorText(document.getCharsSequence());

        return generateContext;
    }

    @Override
    protected SetObjConfigDO getSetObjConfigDO(GenerateContext generateContext, PsiVariable psiVariable) {
        int repair = 0;
        // 获取PsiClass
        PsiClass psiClass = PsiUtil.resolveClassInType(psiVariable.getType());
        if (psiClass == null) {
            return null;
        }
        Pattern setMtd = Pattern.compile(setRegex);
        // 获取类的set方法并存放起来
        List<String> paramList = new ArrayList<>();
        Map<String, String> paramMtdMap = new HashMap<>();
        Map<String, String> paramNameMap = new HashMap<>();

        List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);
        for (PsiClass psi : psiClassLinkList) {
            MethodVO methodVO = getMethods(psi, setRegex, "set");
            for (String methodName : methodVO.getMethodNameList()) {
                // 替换属性
                String param = setMtd.matcher(methodName).replaceAll("$1").toLowerCase();
                // 保存获取的属性信息
                paramMtdMap.put(param, methodName);
                paramList.add(param);
            }

            for (String fieldName : methodVO.getFieldNameList()) {
                paramNameMap.put(fieldName.toLowerCase(), fieldName);
            }
        }

        return new SetObjConfigDO(psiClass.getName(), psiClass.getQualifiedName(),
                psiVariable.getName(),
                paramList,
                paramMtdMap,
                paramNameMap,
                repair,
                isUsedLombokBuilder(psiClass));
    }

    @Deprecated
    @Override
    protected GetObjConfigDO getGetConfigDOByPsiVariable(GenerateContext generateContext, PsiVariable psiVariable) {
        // 获取 PsiClass 实例
        PsiClass psiClass = PsiUtil.resolveClassInType(psiVariable.getType());

        if (psiClass != null) {
            List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);

            Map<String, String> paramMtdMap = new HashMap<>();
            Pattern getM = Pattern.compile(getRegex);

            for (PsiClass psi : psiClassLinkList) {
                MethodVO methodVO = getMethods(psi, getRegex, "get");
                for (String methodName : methodVO.getMethodNameList()) {
                    String param = getM.matcher(methodName).replaceAll("$1").toLowerCase();
                    paramMtdMap.put(param, methodName);
                }
            }

            return new GetObjConfigDO(psiClass.getQualifiedName(), psiClass.getName(), psiVariable.getName(), paramMtdMap);
        }
        return null;
    }

    @Override
    protected GetObjConfigDO getGetConfigDOByPsiTypeAndExpressionText(GenerateContext generateContext, PsiType type, String expressionText) {
        // 获取 PsiClass 实例
        PsiClass psiClass = PsiUtil.resolveClassInType(type);

        if (psiClass != null) {
            List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);

            Map<String, String> paramMtdMap = new HashMap<>();
            Pattern getM = Pattern.compile(getRegex);

            for (PsiClass psi : psiClassLinkList) {
                MethodVO methodVO = getMethods(psi, getRegex, "get");
                for (String methodName : methodVO.getMethodNameList()) {
                    String param = getM.matcher(methodName).replaceAll("$1").toLowerCase();
                    paramMtdMap.put(param, methodName);
                }
            }

            return new GetObjConfigDO(psiClass.getQualifiedName(), psiClass.getName(), expressionText, paramMtdMap);
        }
        return null;
    }

    @Override
    protected void convertSetting(Project project, GenerateContext generateContext, SetObjConfigDO setObjConfigDO, GetObjConfigDO getObjConfigDO) {
        ShowSettingsUtil.getInstance().editConfigurable(project, new ConvertSettingUI(project, generateContext, setObjConfigDO, getObjConfigDO));
    }

    @Override
    protected void weavingSetGetCode(GenerateContext generateContext, SetObjConfigDO setObjConfigDO, GetObjConfigDO getObjConfigDO) {
        Application application = ApplicationManager.getApplication();

        // 获取空格位置长度
//        int distance = Utils.getWordStartOffset(generateContext.getEditorText(), generateContext.getOffset()) - generateContext.getStartOffset() - setObjConfigDO.getRepair();

        // 获取缩进长度
        int indentLength = getIndentLength(generateContext.getDocument(), generateContext.getLineNumber());

        application.runWriteAction(() -> {
            StringBuilder blankSpace = new StringBuilder();
            for (int i = 0; i < indentLength; i++) {
                blankSpace.append(" ");
            }

            int lineNumberCurrent = generateContext.getDocument().getLineNumber(generateContext.getOffset()) + 1;

            /*
             * 判断是使用了 Lombok Builder 模式
             * UserDTO userDTO = UserDTO.builder()
             *              .userId(userVO.getUserId())
             *              .userName(userVO.getUserName())
             *              .userAge(userVO.getUserAge())
             *              .build();
             */
            if (setObjConfigDO.isLombokBuilder()) {
                int finalLineNumberCurrent = lineNumberCurrent;
                WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                    String clazzName = setObjConfigDO.getClazzName();
                    String clazzParam = setObjConfigDO.getClazzParamName();
                    int lineEndOffset = generateContext.getDocument().getLineEndOffset(finalLineNumberCurrent - 1);
                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(finalLineNumberCurrent - 1);
                    generateContext.getDocument().deleteString(lineStartOffset, lineEndOffset);
                    generateContext.getDocument().insertString(generateContext.getDocument().getLineStartOffset(finalLineNumberCurrent - 1), blankSpace + clazzName + " " + clazzParam + " = " + setObjConfigDO.getClazzName() + ".builder()");
                });

                List<String> setMtdList = setObjConfigDO.getParamList();
                for (String param : setMtdList) {
                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                    WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                        String builderMethod = blankSpace + blankSpace.toString() + "." + setObjConfigDO.getParamNameMap().get(param) + "(" + (null == getObjConfigDO.getParamMtdMap().get(param) ? "" : getObjConfigDO.getClazzParam() + "." + getObjConfigDO.getParamMtdMap().get(param) + "()") + ")";
                        generateContext.getDocument().insertString(lineStartOffset, builderMethod + "\n");
                        generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                        generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                    });
                }

                int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                    generateContext.getDocument().insertString(lineStartOffset, blankSpace + blankSpace.toString() + ".build();\n");
                    generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                    generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                });

            } else {
                List<String> setMtdList = setObjConfigDO.getParamList();
                for (String param : setMtdList) {
                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                    WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                        generateContext.getDocument().insertString(lineStartOffset, blankSpace + setObjConfigDO.getClazzParamName() + "." + setObjConfigDO.getParamMtdMap().get(param) + "(" + (null == getObjConfigDO.getParamMtdMap().get(param) ? "" : getObjConfigDO.getClazzParam() + "." + getObjConfigDO.getParamMtdMap().get(param) + "()") + ");\n");
                        generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                        generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                    });
                }
            }

            int finalLineNumberCurrent = lineNumberCurrent;
            WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                generateContext.getEditor().getCaretModel().moveToOffset(generateContext.getDocument().getLineStartOffset(finalLineNumberCurrent));
            });
        });

    }

    private int getIndentLength(Document document, int lineNumber) {
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        String lineContent = document.getText(new TextRange(lineStartOffset, lineEndOffset));

        int indentation = 0;
        for (char c : lineContent.toCharArray()) {
            if (c == ' ') {
                indentation++;
            } else if (c == '\t') {
                indentation += 4; // 假设一个制表符等于4个空格
            } else {
                break;
            }
        }
        return indentation;
    }

    @Deprecated
    protected GetObjConfigDO getObjConfigDOByClipboardText(GenerateContext generateContext) {
        // 获取剪切板信息 【实际使用可补充一些必要的参数判断】
        String systemClipboardText = Utils.getSystemClipboardText().trim();

        // 按照默认规则提取信息，例如：UserDto userDto
        String[] split = systemClipboardText.split("\\s");

        if (split.length < 2) {
            return new GetObjConfigDO("", null, null, new HashMap<>());
        }

        // 摘取复制对象中的类和属性，同时支持复制 cn.xxx.class
        String clazzName;
        String clazzParam = split[1].trim();

        String clazzNameImport = "";
        String clazzNameStr = split[0].trim();
        if (clazzNameStr.indexOf(".") > 0) {
            clazzName = clazzNameStr.substring(clazzNameStr.lastIndexOf(".") + 1);
            clazzNameImport = clazzNameStr;
        } else {
            clazzName = split[0].trim();
        }

        // 获取同名类集合
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(generateContext.getProject()).getClassesByName(clazzName, GlobalSearchScope.allScope(generateContext.getProject()));

        // 上下文检测，找到符合的复制类
        PsiClass psiContextClass = null;
        // 相同类名处理
        if (psiClasses.length > 1) {
            // 获取比对包文本
            List<String> importList;
            if (!"".equals(clazzNameImport)) {
                importList = Collections.singletonList(clazzNameImport);
            } else {
                importList = getImportList(generateContext.getDocument().getText());
            }
            // 循环比对，通过引入的包名与类做包名做对比
            for (PsiClass psiClass : psiClasses) {
                String qualifiedName = Objects.requireNonNull(psiClass.getQualifiedName());
                String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                if (importList.contains(packageName)) {
                    psiContextClass = psiClass;
                    break;
                }
            }
            // 同包下比对
            if (null == psiContextClass) {
                String psiFilePackageName = ((PsiJavaFileImpl) generateContext.getPsiFile()).getPackageName();
                for (PsiClass psiClass : psiClasses) {
                    String qualifiedName = Objects.requireNonNull(psiClass.getQualifiedName());
                    String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                    if (psiFilePackageName.equals(packageName)) {
                        psiContextClass = psiClass;
                        break;
                    }
                }
            }
        }

        if (null == psiContextClass) {
            psiContextClass = psiClasses[0];
        }

        List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiContextClass);

        Map<String, String> paramMtdMap = new HashMap<>();
        Pattern getM = Pattern.compile(getRegex);

        for (PsiClass psi : psiClassLinkList) {
            MethodVO methodVO = getMethods(psi, getRegex, "get");
            for (String methodName : methodVO.getMethodNameList()) {
                String param = getM.matcher(methodName).replaceAll("$1").toLowerCase();
                paramMtdMap.put(param, methodName);
            }
        }

        return new GetObjConfigDO(psiContextClass.getQualifiedName(), clazzName, clazzParam, paramMtdMap);
    }
}
