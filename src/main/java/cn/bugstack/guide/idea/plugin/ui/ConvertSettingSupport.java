package cn.bugstack.guide.idea.plugin.ui;

import cn.bugstack.guide.idea.plugin.domain.model.GenerateContext;
import cn.bugstack.guide.idea.plugin.domain.model.GetObjConfigDO;
import cn.bugstack.guide.idea.plugin.domain.model.SetObjConfigDO;
import cn.bugstack.guide.idea.plugin.infrastructure.DataSetting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;

import javax.swing.*;
import java.util.List;

/**
 * @description: 处理逻辑行为
 * @author: 小傅哥，微信：fustack
 * @date: 2022/1/9
 * @github: https://github.com/fuzhengwei
 * @Copyright: 公众号：bugstack虫洞栈 | 博客：https://bugstack.cn - 沉淀、分享、成长，让自己和他人都能有所收获！
 */
public class ConvertSettingSupport {

    private GenerateContext generateContext;
    private SetObjConfigDO setObjConfigDO;
    private GetObjConfigDO getObjConfigDO;

    protected DataSetting.DataState state;

    public ConvertSettingSupport(Project project, GenerateContext generateContext, SetObjConfigDO setObjConfigDO, GetObjConfigDO getObjConfigDO) {
        // 从数据记录中选择
        this.generateContext = generateContext;
        this.setObjConfigDO = setObjConfigDO;
        this.getObjConfigDO = getObjConfigDO;

        // 配置数据
        state = DataSetting.getInstance(project).getState();
    }

    protected String getFromLabelValText() {
        String qualifiedName = getObjConfigDO.getQualifiedName();
        if ("".equals(qualifiedName)) {
            return "您尚未复制被转换对象，例如：X x = new X() 需要复制 X x";
        }
        return qualifiedName;
    }

    protected String getToLabelValText() {
        String qualifiedName = setObjConfigDO.getQualifiedName();
        if ("".equals(qualifiedName)) {
            return "您尚未定位转换对象 Y y，例如把鼠标定位到对象 Y 或者 y 上";
        }
        return qualifiedName;
    }

    protected String[] getTableTitle() {
        return new String[]{"", setObjConfigDO.getClazzName(), "".equals(getObjConfigDO.getClazzName()) ? "Null" : getObjConfigDO.getClazzName()};
    }

    protected Object[][] getTableData() {
        List<String> setMtdList = setObjConfigDO.getParamList();
        Object[][] data = new Object[setMtdList.size()][3];
        for (int i = 0; i < setMtdList.size(); i++) {
            data[i][0] = Boolean.FALSE;
            // set info
            String param = setMtdList.get(i);
            data[i][1] = setObjConfigDO.getClazzParamName() + "." + setObjConfigDO.getParamMtdMap().get(param);
            // get info
            String getStr = getObjConfigDO.getParamMtdMap().get(param);
            if (null == getStr) continue;
            data[i][2] = getObjConfigDO.getClazzParam() + "." + getObjConfigDO.getParamMtdMap().get(param);
        }
        return data;
    }

    protected void weavingSetGetCode(JTable convertTable) {

        Application application = ApplicationManager.getApplication();
        // 获取空格位置长度
        // int distance = Utils.getWordStartOffset(generateContext.getEditorText(), generateContext.getOffset()) - generateContext.getStartOffset() - setObjConfigDO.getRepair();
        // 获取缩进长度
        int indentLength = getIndentLength(generateContext.getDocument(), generateContext.getLineNumber());
        application.runWriteAction(() -> {
            StringBuilder blankSpace = new StringBuilder();
            for (int i = 0; i < indentLength; i++) {
                blankSpace.append(" ");
            }

            int lineNumberCurrent = generateContext.getDocument().getLineNumber(generateContext.getOffset()) + 1;

            // 判断是否使用了 Lombok 标签的 Builder 且开启了使用 Lombok Builder
            if (setObjConfigDO.isLombokBuilder() && state.isUsedLombokBuilder()) {
                /*
                 * 判断是使用了 Lombok Builder 模式
                 * UserDTO userDTO = UserDTO.builder()
                 *              .userId(userVO.getUserId())
                 *              .userName(userVO.getUserName())
                 *              .userAge(userVO.getUserAge())
                 *              .build();
                 */
                int finalLineNumberCurrent = lineNumberCurrent;
                WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                    String clazzName = setObjConfigDO.getClazzName();
                    String clazzParam = setObjConfigDO.getClazzParamName();
                    int lineEndOffset = generateContext.getDocument().getLineEndOffset(finalLineNumberCurrent - 1);
                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(finalLineNumberCurrent - 1);
                    generateContext.getDocument().deleteString(lineStartOffset, lineEndOffset);
                    generateContext.getDocument().insertString(generateContext.getDocument().getLineStartOffset(finalLineNumberCurrent - 1), blankSpace + clazzName + " " + clazzParam + " = " + setObjConfigDO.getClazzName() + ".builder()");
                });

                // setNullRadioButton -> 全部清空，则默认生成空转换
                if ("setNullRadioButton".equals(state.getSelectRadio())) {
                    List<String> setMtdList = setObjConfigDO.getParamList();
                    for (String param : setMtdList) {
                        int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                        WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                            String builderMethod = blankSpace + blankSpace.toString() + "." + setObjConfigDO.getParamNameMap().get(param) + "()";
                            generateContext.getDocument().insertString(lineStartOffset, builderMethod + "\n");
                            generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                            generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        });
                    }

                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent);
                    WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                        generateContext.getDocument().insertString(lineStartOffset, blankSpace + blankSpace.toString() + ".build();\n");
                        generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                        generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                    });
                } else {
                    // selectAllRadioButton、selectExistRadioButton -> 按照选择进行转换插入
                    int rowCount = convertTable.getRowCount();
                    for (int idx = 0; idx < rowCount; idx++) {
                        boolean isSelected = (boolean) convertTable.getValueAt(idx, 0);
                        if (!isSelected) continue;

                        int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                        Object setVal = convertTable.getValueAt(idx, 1);
                        Object getVal = convertTable.getValueAt(idx, 2);

                        WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                            String setValParam = setVal.toString().substring(setVal.toString().indexOf("set") + 3).toLowerCase();
                            String builderMethod = blankSpace + blankSpace.toString() + "." + setObjConfigDO.getParamNameMap().get(setValParam) + "(" + (null == getVal ? "" : getVal + "()") + ")";

                            generateContext.getDocument().insertString(lineStartOffset, builderMethod + "\n");
                            generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                            generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        });
                    }
                }

                int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                    generateContext.getDocument().insertString(lineStartOffset, blankSpace + blankSpace.toString() + ".build();\n");
                    generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                    generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                });
            }
            else {
                // setNullRadioButton -> 全部清空，则默认生成空转换
                if ("setNullRadioButton".equals(state.getSelectRadio())) {
                    List<String> setMtdList = setObjConfigDO.getParamList();
                    for (String param : setMtdList) {
                        int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);

                        WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                            generateContext.getDocument().insertString(lineStartOffset, blankSpace + blankSpace.toString() + setObjConfigDO.getClazzParamName() + "." + setObjConfigDO.getParamMtdMap().get(param) + "();\n");
                            generateContext.getEditor().getCaretModel().moveToOffset(lineStartOffset + 2);
                            generateContext.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        });

                    }
                    return;
                }

                // selectAllRadioButton、selectExistRadioButton -> 按照选择进行转换插入
                int rowCount = convertTable.getRowCount();
                for (int idx = 0; idx < rowCount; idx++) {
                    boolean isSelected = (boolean) convertTable.getValueAt(idx, 0);
                    if (!isSelected) continue;

                    int lineStartOffset = generateContext.getDocument().getLineStartOffset(lineNumberCurrent++);
                    Object setVal = convertTable.getValueAt(idx, 1);
                    Object getVal = convertTable.getValueAt(idx, 2);

                    WriteCommandAction.runWriteCommandAction(generateContext.getProject(), () -> {
                        generateContext.getDocument().insertString(lineStartOffset, blankSpace + setVal.toString() + "(" + (null == getVal ? "" : getVal + "()") + ");\n");
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
}
