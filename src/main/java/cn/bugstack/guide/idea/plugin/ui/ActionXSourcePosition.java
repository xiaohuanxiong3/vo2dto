package cn.bugstack.guide.idea.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

/**
 * @Description
 * @Author Handsome Young
 * @Date 2024/8/16 17:22
 */
public class ActionXSourcePosition implements XSourcePosition {

    private final int line;

    private final int offset;

    private final VirtualFile file;

    public ActionXSourcePosition(int line, int offset, VirtualFile file) {
        this.line = line;
        this.offset = offset;
        this.file = file;
    }

    @Override
    public int getLine() {
        return this.line;
    }

    @Override
    public int getOffset() {
        return this.offset;
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return this.file;
    }

    /**
     * 暂时返回null（暂时没用到）
     * @param project
     * @return
     */
    @Override
    public @NotNull Navigatable createNavigatable(@NotNull Project project) {
        return null;
    }

}
