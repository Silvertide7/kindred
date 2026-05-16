package net.silvertide.kindred.client.screen;

import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.network.BondView;
import net.silvertide.kindred.network.packet.C2SRenameBond;

import java.util.Optional;
import java.util.UUID;

public final class RenameEditor {
    private static final int MAX_NAME_LENGTH = 32;

    private static final int GLFW_KEY_ENTER = 257;
    private static final int GLFW_KEY_NUMPAD_ENTER = 335;
    private static final int GLFW_KEY_ESCAPE = 256;
    private static final int GLFW_KEY_BACKSPACE = 259;

    private static final char FORMATTING_PREFIX = '§';

    private UUID editingBondId;
    private String editBuffer = "";

    public boolean isEditing() {
        return editingBondId != null;
    }

    public boolean isEditingBond(UUID bondId) {
        return editingBondId != null && editingBondId.equals(bondId);
    }

    public String editBuffer() {
        return editBuffer;
    }

    public void startEditing(BondView bond) {
        editingBondId = bond.bondId();
        editBuffer = bond.displayName().orElse("");
    }

    public void cancelEditing() {
        editingBondId = null;
        editBuffer = "";
    }

    public void commitEditing() {
        if (editingBondId == null) return;
        String trimmedName = editBuffer.trim();
        Optional<String> newName = trimmedName.isEmpty() ? Optional.empty() : Optional.of(trimmedName);
        PacketDistributor.sendToServer(new C2SRenameBond(editingBondId, newName));
        editingBondId = null;
        editBuffer = "";
    }

    public boolean tryHandleCharTyped(char codePoint) {
        if (!isEditing()) return false;
        if (codePoint == FORMATTING_PREFIX || Character.isISOControl(codePoint)) return true;
        if (editBuffer.length() < MAX_NAME_LENGTH) {
            editBuffer = editBuffer + codePoint;
        }
        return true;
    }

    public boolean tryHandleKeyPressed(int keyCode) {
        if (!isEditing()) return false;
        switch (keyCode) {
            case GLFW_KEY_ENTER, GLFW_KEY_NUMPAD_ENTER -> commitEditing();
            case GLFW_KEY_ESCAPE -> cancelEditing();
            case GLFW_KEY_BACKSPACE -> {
                if (!editBuffer.isEmpty()) {
                    editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
            }
            default -> { /* swallow so global keybinds don't fire while typing */ }
        }
        return true;
    }
}
