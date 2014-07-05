package net.sourceforge.vrapper.eclipse.interceptor;

import net.sourceforge.vrapper.eclipse.activator.VrapperPlugin;
import net.sourceforge.vrapper.utils.CaretType;
import net.sourceforge.vrapper.vim.DefaultEditorAdaptor;
import net.sourceforge.vrapper.vim.Options;
import net.sourceforge.vrapper.vim.modes.AbstractVisualMode;
import net.sourceforge.vrapper.vim.modes.EditorMode;
import net.sourceforge.vrapper.vim.modes.InsertMode;
import net.sourceforge.vrapper.vim.modes.NormalMode;
import net.sourceforge.vrapper.vim.modes.TempVisualMode;
import net.sourceforge.vrapper.vim.modes.TemporaryMode;
import net.sourceforge.vrapper.vim.modes.VisualMode;

import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;

public class SelectionVisualHandler implements ISelectionChangedListener {

    private DefaultEditorAdaptor editorAdaptor;

    public SelectionVisualHandler(DefaultEditorAdaptor editorAdaptor) {
        this.editorAdaptor = editorAdaptor;
    }

    public void selectionChanged(SelectionChangedEvent event) {
        if ( ! VrapperPlugin.isVrapperEnabled()
                || ! VrapperPlugin.isMouseDown()
                || ! (event.getSelection() instanceof TextSelection)
                || ! editorAdaptor.getConfiguration().get(Options.VISUAL_MOUSE))
            return;

        TextSelection selection = (TextSelection) event.getSelection();
        // selection.isEmpty() is false even if length == 0, don't use it
        if (selection instanceof TextSelection) {
            if (selection.getLength() == 0) {
                EditorMode currentMode = editorAdaptor.getMode(editorAdaptor.getCurrentModeName());
                // User cleared selection or moved caret with mouse in a temporary mode.
                if(currentMode instanceof TemporaryMode) {
                    editorAdaptor.changeModeSafely(InsertMode.NAME);
                } else if(currentMode instanceof AbstractVisualMode){
                    editorAdaptor.changeModeSafely(NormalMode.NAME);
                }
            } else if(selection.getLength() != 0) {
                // Fix caret type
                if (editorAdaptor.getConfiguration().get(Options.SELECTION).equals("inclusive")) {
                    CaretType type = CaretType.LEFT_SHIFTED_RECTANGULAR;
                    if (editorAdaptor.getSelection().isReversed()) {
                        type = CaretType.RECTANGULAR;
                    }
                    editorAdaptor.getCursorService().setCaret(type);
                }
                if(NormalMode.NAME.equals(editorAdaptor.getCurrentModeName())) {
                    editorAdaptor.changeModeSafely(VisualMode.NAME, AbstractVisualMode.KEEP_SELECTION_HINT);
                }
                else if (InsertMode.NAME.equals(editorAdaptor.getCurrentModeName())) {
                    editorAdaptor.changeModeSafely(TempVisualMode.NAME,
                            AbstractVisualMode.KEEP_SELECTION_HINT, InsertMode.DONT_MOVE_CURSOR);
                }
            }
        }
    }
}