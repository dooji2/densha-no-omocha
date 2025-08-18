package com.dooji.dno.gui;

import com.dooji.dno.TrainModClient;
import com.dooji.dno.network.TrainModClientNetworking;
import com.dooji.dno.network.payloads.UpdateTrackSegmentPayload;
import com.dooji.dno.network.payloads.UpdateTrainConfigPayload;
import com.dooji.dno.track.TrackRenderer;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.train.TrainClient;
import com.dooji.dno.train.TrainConfigLoader;
import com.dooji.dno.train.TrainManagerClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TrackConfigScreen extends Screen {
    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 250;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_SPACING = 5;

    private static final int SIDEBAR_WIDTH = 80;
    private static final int FOOTER_HEIGHT = 28;
    private static final int CONTENT_PADDING = 6;
    private static final int SEARCH_HEIGHT = 20;

    private final TrackSegment segment;
    private ButtonWidget segmentTypeButton;

    private ButtonWidget sidebarTrackButton;
    private ButtonWidget sidebarTrainButton;
    private ButtonWidget trainModeListButton;
    private ButtonWidget trainModeConfigButton;
    private ButtonWidget refreshPathButton;
    private TextFieldWidget dwellTimeField;
    private TextFieldWidget slopeCurvatureField;
    private int hoveredButtonIndex = -1;

    private String selectedTrackId;
    private String selectedType;
    private boolean isTrainConfigTab = false;
    private boolean isTrainConfigurationView = false;
    private int dwellTimeSeconds;

    private List<String> availableTrackIds;
    private List<String> availableTrainIds;
    private List<String> availableTypes;
    private List<String> currentCarriages;
    private int selectedTrackIdIndex = 0;
    private int selectedTypeIndex = 0;

    private int scrollOffset = 0;
    private int horizontalScrollOffset = 0;

    private boolean isDraggingScrollbar = false;
    private boolean isDraggingHorizontalScrollbar = false;

    private int dragStartY = 0;
    private int dragStartX = 0;
    private int dragStartScrollOffset = 0;
    private int dragStartHorizontalScrollOffset = 0;

    private String trainId;
    private TextFieldWidget trackSearchField;
    private TextFieldWidget trainSearchField;
    private List<String> filteredTrackIds;
    private List<String> filteredTrainIds;
    private int lastListX;
    private int lastListY;
    private int lastListWidth;
    private int lastListHeight;

    private static final Identifier BUTTON_NORMAL = Identifier.ofVanilla("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.ofVanilla("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.ofVanilla("widget/button_disabled");

    private int getGuiY() {
        return this.height / 2 - GUI_HEIGHT / 2;
    }

    public TrackConfigScreen(TrackSegment segment) {
        super(Text.translatable("gui.dno.track_config.title"));

        this.segment = segment;
        this.selectedTrackId = segment.getModelId();
        this.selectedType = segment.getType();
        this.dwellTimeSeconds = segment.getDwellTimeSeconds();
        this.currentCarriages = new ArrayList<>();
        this.trainId = segment.getTrainId();

        if (this.trainId == null) {
            this.trainId = "train_" + System.currentTimeMillis();
        }

        loadCurrentTrainConfig();
    }

    @Override
    protected void init() {
        super.init();

        loadAvailableOptions();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int guiX = centerX - GUI_WIDTH / 2;
        int guiY = centerY - GUI_HEIGHT / 2;

        int sidebarX = guiX + CONTENT_PADDING;
        int sidebarY = guiY + CONTENT_PADDING;
        int sidebarButtonWidth = SIDEBAR_WIDTH - CONTENT_PADDING * 2;
        int sidebarButtonHeight = 20;

        this.sidebarTrackButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.tab.track"), button -> {
            isTrainConfigTab = false;
            isTrainConfigurationView = false;
            scrollOffset = 0;
            updateTrainConfigTabVisibility();
        }).dimensions(sidebarX, sidebarY, sidebarButtonWidth, sidebarButtonHeight).build();
        this.addDrawableChild(this.sidebarTrackButton);

        this.sidebarTrainButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.tab.train"), button -> {
            isTrainConfigTab = true;
            isTrainConfigurationView = false;
            scrollOffset = 0;
            loadCurrentTrainConfig();
            updateTrainConfigTabVisibility();
        }).dimensions(sidebarX, sidebarY + sidebarButtonHeight + BUTTON_SPACING, sidebarButtonWidth, sidebarButtonHeight).build();
        this.addDrawableChild(this.sidebarTrainButton);

        int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
        int contentY = guiY + CONTENT_PADDING;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;

        this.trainModeListButton = ButtonWidget.builder(Text.translatable("gui.dno.train_config.mode.list"), button -> {
            isTrainConfigurationView = false;
            scrollOffset = 0;
            updateTrainConfigTabVisibility();
        }).dimensions(contentX, contentY, 50, 20).build();
        this.addDrawableChild(this.trainModeListButton);

        this.trainModeConfigButton = ButtonWidget.builder(Text.translatable("gui.dno.train_config.mode.config"), button -> {
            isTrainConfigurationView = true;
            scrollOffset = 0;
            if (this.currentCarriages == null || this.currentCarriages.isEmpty()) {
                loadCurrentTrainConfig();
            }
            updateTrainConfigTabVisibility();
        }).dimensions(contentX + 55, contentY, 90, 20).build();
        this.addDrawableChild(this.trainModeConfigButton);

        this.trackSearchField = new TextFieldWidget(this.textRenderer, contentX, contentY, Math.min(BUTTON_WIDTH, contentWidth), SEARCH_HEIGHT, Text.translatable("gui.dno.common.search"));
        this.trackSearchField.setChangedListener(text -> updateFilters());
        this.addDrawableChild(this.trackSearchField);

        this.trainSearchField = new TextFieldWidget(this.textRenderer, contentX, contentY + 24, Math.min(BUTTON_WIDTH, contentWidth), SEARCH_HEIGHT, Text.translatable("gui.dno.common.search"));
        this.trainSearchField.setChangedListener(text -> updateFilters());
        this.addDrawableChild(this.trainSearchField);

        int typeButtonY = contentY + 60;
        this.segmentTypeButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.type", selectedType), button -> {
            selectedTypeIndex = (selectedTypeIndex + 1) % availableTypes.size();
            selectedType = availableTypes.get(selectedTypeIndex);
            button.setMessage(Text.translatable("gui.dno.track_config.type", selectedType));
            updateTrainConfigTabVisibility();
        }).dimensions(contentX, typeButtonY, Math.min(BUTTON_WIDTH, contentWidth), 20).build();
        this.addDrawableChild(this.segmentTypeButton);

        int dwellY = typeButtonY + 25;
        this.dwellTimeField = new TextFieldWidget(this.textRenderer, contentX, dwellY, 100, 20, Text.translatable("gui.dno.track_config.dwell_time"));
        this.dwellTimeField.setText(String.valueOf(dwellTimeSeconds));
        this.addDrawableChild(this.dwellTimeField);

        int curvatureY = dwellY + 25;
        this.slopeCurvatureField = new TextFieldWidget(this.textRenderer, contentX, curvatureY, 100, 20, Text.translatable("gui.dno.track_config.curvature"));
        this.slopeCurvatureField.setText(String.valueOf(segment.getSlopeCurvature()));
        this.addDrawableChild(this.slopeCurvatureField);

        int footerY = guiY + GUI_HEIGHT - FOOTER_HEIGHT + (FOOTER_HEIGHT - 20) / 2;
        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("gui.dno.common.save"), button -> saveAndClose()).dimensions(contentX, footerY, 80, 20).build();
        this.addDrawableChild(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.dno.common.cancel"), button -> closeWithoutSaving()).dimensions(contentX + 85, footerY, 80, 20).build();
        this.addDrawableChild(cancelButton);

        this.refreshPathButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.refresh_path"), button -> {
            var payload = new com.dooji.dno.network.payloads.RefreshTrainPathPayload(trainId, segment.start(), segment.end());
            TrainModClientNetworking.sendToServer(payload);
        }).dimensions(contentX + 150, contentY, 120, 20).build();
        this.addDrawableChild(this.refreshPathButton);

        updateFilters();
        updateTrainConfigTabVisibility();
    }

    private void loadAvailableOptions() {
        availableTrackIds = new ArrayList<>();
        
        Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
        if (trackTypes == null || trackTypes.isEmpty()) {
            TrackRenderer.loadTrackTypes(MinecraftClient.getInstance().getResourceManager());
            trackTypes = TrackRenderer.getTrackTypes();
        }
        
        if (trackTypes != null && !trackTypes.isEmpty()) {
            availableTrackIds.addAll(trackTypes.keySet());
        }
        
        if (availableTrackIds.isEmpty()) {
            availableTrackIds.add("default");
        }

        availableTypes = List.of("normal", "platform", "siding");

        selectedTrackIdIndex = availableTrackIds.indexOf(selectedTrackId);
        if (selectedTrackIdIndex == -1) {
            selectedTrackIdIndex = 0;
            selectedTrackId = availableTrackIds.get(0);
        }

        selectedTypeIndex = availableTypes.indexOf(selectedType);
        if (selectedTypeIndex == -1) {
            selectedTypeIndex = 0;
            selectedType = availableTypes.get(0);
        }

        availableTrainIds = new ArrayList<>();
        Map<String, TrainConfigLoader.TrainTypeData> trainTypes = TrainConfigLoader.getTrainTypes();
        if (trainTypes != null) {
            availableTrainIds.addAll(trainTypes.keySet());
        }
    }

    private void loadCurrentTrainConfig() {
        Map<String, TrainClient> trains = TrainManagerClient.getTrainsFor(MinecraftClient.getInstance().world);
        TrainClient train = trains.get(trainId);
        if (train != null) {
            this.currentCarriages = new ArrayList<>(train.getCarriageIds());
        }
    }

    private void updateTrainConfigTabVisibility() {
        boolean isSiding = "siding".equals(selectedType);
        boolean isPlatform = "platform".equals(selectedType);
        if (this.sidebarTrainButton != null) {
            this.sidebarTrainButton.active = isSiding;
        }

        boolean trackView = !isTrainConfigTab;
        this.segmentTypeButton.visible = trackView;
        this.dwellTimeField.visible = isPlatform && trackView;
        boolean isSlope = segment.start().getY() != segment.end().getY();
        this.slopeCurvatureField.visible = isSlope && trackView;

        if (!isSiding && isTrainConfigTab) {
            isTrainConfigTab = false;
        }

        boolean trainView = isTrainConfigTab;
        if (this.trainModeListButton != null) {
            this.trainModeListButton.visible = trainView;
        }

        if (this.trainModeConfigButton != null) {
            this.trainModeConfigButton.visible = trainView;
        }

        if (this.refreshPathButton != null) {
            this.refreshPathButton.visible = trainView && isTrainConfigurationView;
        }

        if (this.trackSearchField != null) {
            this.trackSearchField.visible = trackView;
        }

        if (this.trainSearchField != null) {
            this.trainSearchField.visible = trainView && !isTrainConfigurationView;
        }

        updateFilters();
    }

    private void updateFilters() {
        if (availableTrackIds != null) {
            String q = this.trackSearchField != null ? this.trackSearchField.getText() : "";
            if (q != null && !q.isEmpty()) {
                String qq = q.toLowerCase();
                List<String> out = new ArrayList<>();

                for (String id : availableTrackIds) {
                    if (id.toLowerCase().contains(qq)) {
                        out.add(id);
                    }
                }

                this.filteredTrackIds = out;
            } else {
                this.filteredTrackIds = null;
            }
        }

        if (availableTrainIds != null) {
            String q2 = this.trainSearchField != null ? this.trainSearchField.getText() : "";
            if (q2 != null && !q2.isEmpty()) {
                String qq2 = q2.toLowerCase();
                List<String> out2 = new ArrayList<>();

                for (String id : availableTrainIds) {
                    if (id.toLowerCase().contains(qq2)) {
                        out2.add(id);
                    }
                }

                this.filteredTrainIds = out2;
            } else {
                this.filteredTrainIds = null;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int guiX = centerX - GUI_WIDTH / 2;
        int guiY = getGuiY();

        int sidebarRight = guiX + SIDEBAR_WIDTH + CONTENT_PADDING;
        context.fill(sidebarRight, guiY, sidebarRight + 1, guiY + GUI_HEIGHT, 0xFF2B2B2B);
        int footerTop = guiY + GUI_HEIGHT - FOOTER_HEIGHT;
        context.fill(guiX, footerTop, guiX + GUI_WIDTH, footerTop + 1, 0xFF2B2B2B);

        int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
        int contentY = guiY + CONTENT_PADDING;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;

        int listX = contentX;
        int listY;

        if (isTrainConfigTab) {
            if (isTrainConfigurationView) {
                listY = contentY + 24 + BUTTON_SPACING;
            } else {
                listY = contentY + 24 + BUTTON_SPACING + 24 + BUTTON_SPACING;
            }
        } else {
            listY = contentY + SEARCH_HEIGHT + BUTTON_SPACING;
        }

        int listWidth = Math.min(BUTTON_WIDTH, contentWidth);
        int listHeight;
        if (!isTrainConfigTab) {
            boolean isPlatform = "platform".equals(selectedType);
            boolean isSlope = segment.start().getY() != segment.end().getY();
            int bottomY = guiY + GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING;
            int typeButtonY = bottomY - 20;

            if (this.segmentTypeButton != null) {
                this.segmentTypeButton.setX(contentX);
                this.segmentTypeButton.setY(typeButtonY);
                this.segmentTypeButton.setWidth(listWidth);
                this.segmentTypeButton.visible = true;
            }

            int nextY = typeButtonY - BUTTON_SPACING;
            if (isPlatform && this.dwellTimeField != null) {
                int dwellY = nextY - 20;
                this.dwellTimeField.setX(contentX);
                this.dwellTimeField.setY(dwellY);
                nextY = dwellY - (12 + BUTTON_SPACING);
            }

            if (isSlope && this.slopeCurvatureField != null) {
                int curvatureY = nextY - 20;
                this.slopeCurvatureField.setX(contentX);
                this.slopeCurvatureField.setY(curvatureY);
                nextY = curvatureY - (12 + BUTTON_SPACING);
            }

            listHeight = Math.max(40, nextY - listY);
        } else {
            int contentHeight = GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;
            listHeight = contentHeight - (listY - contentY) - BUTTON_SPACING;

            if (listHeight < 40) listHeight = 40;
        }

        this.lastListX = listX;
        this.lastListY = listY;
        this.lastListWidth = listWidth;
        this.lastListHeight = listHeight;

        updateHoveredButton(mouseX, mouseY, listX, listY);
        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        if (isTrainConfigTab) {
            if (isTrainConfigurationView) {
                renderTrainConfigurationGrid(context, listX, listY, listWidth, listHeight, mouseX, mouseY, delta);
            } else {
            renderTrainConfigList(context, listX, listY, mouseX, mouseY, delta);
            }
        } else {
            renderTrackConfigList(context, listX, listY, mouseX, mouseY, delta);
        }

        context.disableScissor();

        int totalHeight = calculateTotalHeight(listWidth);
        if (totalHeight > listHeight) {
            renderScrollbar(context, listX + listWidth + 5, listY, listHeight, totalHeight, mouseX, mouseY);
        }

        if (!isTrainConfigTab) {
            renderTrackConfigInfo(context, contentX + contentWidth / 2, guiY);
            if ("platform".equals(selectedType)) {
                int labelY = this.dwellTimeField.getY() - 12;
                context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.track_config.dwell_time_label"), this.dwellTimeField.getX(), labelY, 0xCCCCCC);
            }

            boolean isSlope = segment.start().getY() != segment.end().getY();
            if (isSlope) {
                int labelY2 = this.slopeCurvatureField.getY() - 12;
                context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.track_config.curvature_label"), this.slopeCurvatureField.getX(), labelY2, 0xCCCCCC);
            }
        }

        if (this.trainModeListButton != null) {
            this.trainModeListButton.active = isTrainConfigTab;
        }

        if (this.trainModeConfigButton != null) {
            this.trainModeConfigButton.active = isTrainConfigTab;
        }

        if (this.refreshPathButton != null) {
            this.refreshPathButton.visible = isTrainConfigTab && isTrainConfigurationView;
        }
    }

    private void renderTrackConfigList(DrawContext context, int listX, int listY, int mouseX, int mouseY, float delta) {
        int currentY = listY;
        List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
        for (int i = 0; i < items.size(); i++) {
            String trackId = items.get(i);
            Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();

            boolean isTitleOnly = false;
            if (trackTypes != null) {
                TrackRenderer.TrackTypeData trackData = trackTypes.get(trackId);
                if (trackData != null) {
                    boolean hasIcon = trackData.icon() != null;
                    boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();
                    isTitleOnly = !hasIcon && !hasDesc;
                }
            }

            int buttonHeight = isTitleOnly ? 25 : BUTTON_HEIGHT;
            int buttonY = currentY - scrollOffset;

            if (buttonY + buttonHeight >= listY && buttonY <= listY + 10000) {
                renderTrackTypeButton(context, i, listX, buttonY, buttonHeight);
            }

            currentY += buttonHeight + BUTTON_SPACING;
        }
    }

    private void renderTrainConfigList(DrawContext context, int listX, int listY, int mouseX, int mouseY, float delta) {
        int currentY = listY;
        List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
        for (int i = 0; i < items.size(); i++) {
            int buttonY = currentY - scrollOffset;

            if (buttonY + BUTTON_HEIGHT >= listY && buttonY <= listY + 10000) {
                renderTrainTypeButton(context, i, listX, buttonY, mouseX, mouseY, delta);
            }

            currentY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    private void renderTrainConfigurationGrid(DrawContext context, int startX, int startY, int areaWidth, int areaHeight, int mouseX, int mouseY, float delta) {
        int itemWidth = 90;
        int itemHeight = 24;
        int gap = 6;
        int columns = Math.max(1, (areaWidth + gap) / (itemWidth + gap));
        int yBase = startY - scrollOffset;

            for (int i = 0; i < currentCarriages.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int bx = startX + col * (itemWidth + gap);
            int by = yBase + row * (itemHeight + gap);

            if (by + itemHeight < startY || by > startY + areaHeight) {
                continue;
            }

            boolean hover = mouseX >= bx && mouseX <= bx + itemWidth && mouseY >= by && mouseY <= by + itemHeight;
            Identifier tex = hover ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, tex, bx, by, itemWidth, itemHeight);

            String id = currentCarriages.get(i);
            TrainConfigLoader.TrainTypeData td = TrainConfigLoader.getTrainType(id);
            String title = td != null ? td.name() : id;

            context.enableScissor(bx, by, bx + itemWidth, by + itemHeight);
            context.drawTextWithShadow(this.textRenderer, Text.literal(title), bx + 6, by + 7, 0xFFFFFFFF);
                context.disableScissor();

            if (hover) {
                int controlSize = 16;
                int controlsWidth = controlSize * 3 + gap * 2;
                int cx = bx + (itemWidth - controlsWidth) / 2;
                int cy = by + (itemHeight - controlSize) / 2;

                boolean hLeft = mouseX >= cx && mouseX <= cx + controlSize && mouseY >= cy && mouseY <= cy + controlSize;
                boolean hRemove = mouseX >= cx + controlSize + gap && mouseX <= cx + controlSize + gap + controlSize && mouseY >= cy && mouseY <= cy + controlSize;
                boolean hRight = mouseX >= cx + (controlSize + gap) * 2 && mouseX <= cx + (controlSize + gap) * 2 + controlSize && mouseY >= cy && mouseY <= cy + controlSize;

                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, hLeft ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, cx, cy, controlSize, controlSize);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("<"), cx + controlSize / 2, cy + 4, 0xFFFFFFFF);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, hRemove ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, cx + controlSize + gap, cy, controlSize, controlSize);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("x"), cx + controlSize + gap + controlSize / 2, cy + 4, 0xFFFFFFFF);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, hRight ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, cx + (controlSize + gap) * 2, cy, controlSize, controlSize);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(">"), cx + (controlSize + gap) * 2 + controlSize / 2, cy + 4, 0xFFFFFFFF);
            }
        }

        if (!currentCarriages.isEmpty()) {
            int frontIndex = 0;
            int backIndex = currentCarriages.size() - 1;

            int frontCol = frontIndex % columns;
            int frontRow = frontIndex / columns;
            int backCol = backIndex % columns;
            int backRow = backIndex / columns;
            
            int frontX = startX + frontCol * (itemWidth + gap);
            int frontY = startY - scrollOffset + frontRow * (itemHeight + gap) - 10;
            int backX = startX + backCol * (itemWidth + gap);
            int backY = startY - scrollOffset + backRow * (itemHeight + gap) - 10;

            context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.train_config.front"), frontX, frontY, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.train_config.back"), backX, backY, 0xCCCCCC);
        }
    }

    private void renderTrackConfigInfo(DrawContext context, int centerX, int guiY) {
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("gui.dno.track_config.segment_info", segment.start().toShortString(), segment.end().toShortString()),
            centerX,
            guiY + GUI_HEIGHT - 80,
            0xCCCCCC
        );
    }

    private void renderScrollbar(DrawContext context, int x, int y, int height, int totalHeight, int mouseX, int mouseY) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BUTTON_DISABLED, x, y, 10, height);

        int handleHeight = Math.max(10, (height * height) / totalHeight);
        int handleY = y + (scrollOffset * (height - handleHeight)) / (totalHeight - height);

        boolean isHovered = mouseX >= x && mouseX <= x + 10 && mouseY >= handleY && mouseY <= handleY + handleHeight;

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, isHovered ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, x, handleY, 10, handleHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= lastListX && mouseX <= lastListX + lastListWidth && mouseY >= lastListY && mouseY <= lastListY + lastListHeight) {
                if (isTrainConfigTab) {
                    if (isTrainConfigurationView) {
                        handleCarriageConfigClick(mouseX, mouseY, 0, 0, 0);
                } else {
                        handleTrainConfigClick(mouseX, mouseY, lastListX, lastListY);
                    }
                } else {
                    handleTrackConfigClick(mouseX, mouseY, lastListX, lastListY);
                }

                return true;
            }

            if (mouseX >= lastListX + lastListWidth + 5 && mouseX <= lastListX + lastListWidth + 15 && mouseY >= lastListY && mouseY <= lastListY + lastListHeight) {
                isDraggingScrollbar = true;
                dragStartY = (int)mouseY;
                dragStartScrollOffset = scrollOffset;

                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleCarriageConfigClick(double mouseX, double mouseY, int centerX, int topCarY, int carH) {
        if (!isTrainConfigTab || !isTrainConfigurationView) return;

        int centerXLocal = this.width / 2;
        int guiX = centerXLocal - GUI_WIDTH / 2;
        int guiY = getGuiY();

        int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
        int contentY = guiY + CONTENT_PADDING;

        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;
        int listX = contentX;
        int listY = contentY + 24 + BUTTON_SPACING;

        int itemWidth = 90;
        int itemHeight = 24;
        int gap = 6;
        int columns = Math.max(1, (Math.min(lastListWidth, Math.min(BUTTON_WIDTH, contentWidth)) + gap) / (itemWidth + gap));

        for (int i = 0; i < currentCarriages.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int bx = listX + col * (itemWidth + gap);
            int by = listY + row * (itemHeight + gap) - scrollOffset;

            if (by + itemHeight < lastListY || by > lastListY + lastListHeight) continue;
            if (mouseX >= bx && mouseX <= bx + itemWidth && mouseY >= by && mouseY <= by + itemHeight) {
                int controlSize = 16;
                int controlsWidth = controlSize * 3 + gap * 2;

                int cx = bx + (itemWidth - controlsWidth) / 2;
                int cy = by + (itemHeight - controlSize) / 2;

                if (mouseX >= cx && mouseX <= cx + controlSize && mouseY >= cy && mouseY <= cy + controlSize) {
                    if (i > 0) Collections.swap(currentCarriages, i, i - 1);
                } else if (mouseX >= cx + controlSize + gap && mouseX <= cx + controlSize + gap + controlSize && mouseY >= cy && mouseY <= cy + controlSize) {
                    currentCarriages.remove(i);
                } else if (mouseX >= cx + (controlSize + gap) * 2 && mouseX <= cx + (controlSize + gap) * 2 + controlSize && mouseY >= cy && mouseY <= cy + controlSize) {
                    if (i < currentCarriages.size() - 1) Collections.swap(currentCarriages, i, i + 1);
                }

                break;
            }
        }
    }

    private void handleTrackConfigClick(double mouseX, double mouseY, int listX, int listY) {
        int currentY = listY;
        List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
        for (int i = 0; i < items.size(); i++) {
            String trackId = items.get(i);
            Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
            boolean isTitleOnly = false;

            if (trackTypes != null) {
                TrackRenderer.TrackTypeData trackData = trackTypes.get(trackId);
                if (trackData != null) {
                    boolean hasIcon = trackData.icon() != null;
                    boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();
                    isTitleOnly = !hasIcon && !hasDesc;
                }
            }

            int buttonHeight = isTitleOnly ? 25 : BUTTON_HEIGHT;

            int buttonY = currentY - scrollOffset;
            if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                selectedTrackIdIndex = i;
                selectedTrackId = trackId;
                break;
            }

            currentY += buttonHeight + BUTTON_SPACING;
        }
    }

    private void handleTrainConfigClick(double mouseX, double mouseY, int listX, int listY) {
        int currentY = listY;
        List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
        for (String availableTrainId : items) {
            int buttonY = currentY - scrollOffset;
            if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT) {

                int addButtonX = listX + lastListWidth - 30;
                int addButtonY = buttonY;
                int addButtonHeight = BUTTON_HEIGHT;

                if (mouseX >= addButtonX && mouseX <= addButtonX + 25 && mouseY >= addButtonY && mouseY <= addButtonY + addButtonHeight) {
                    if (currentCarriages == null) currentCarriages = new ArrayList<>();
                    currentCarriages.add(availableTrainId);
                }

                break;
            }

            currentY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && button == 0) {
            int guiY = getGuiY();

            int contentY = guiY + CONTENT_PADDING;
            int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;
            int contentHeight = GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;

            int listY;
            if (isTrainConfigTab) {
                if (isTrainConfigurationView) {
                    listY = contentY + 24 + BUTTON_SPACING;
                } else {
                    listY = contentY + 24 + BUTTON_SPACING + 24 + BUTTON_SPACING;
                }
            } else {
                listY = contentY + SEARCH_HEIGHT + BUTTON_SPACING;
            }

            int listHeight = contentHeight - (listY - contentY) - BUTTON_SPACING;
            int totalHeight = calculateTotalHeight(Math.min(BUTTON_WIDTH, contentWidth));

            if (totalHeight > listHeight) {
                int mouseDelta = (int)mouseY - dragStartY;
                int scrollableHeight = totalHeight - listHeight;
                double scrollRatio = (double)mouseDelta / listHeight;

                scrollOffset = dragStartScrollOffset + (int)(scrollRatio * scrollableHeight);
                scrollOffset = Math.max(0, Math.min(scrollOffset, scrollableHeight));
            }

            return true;
        }

        if (isDraggingHorizontalScrollbar && button == 0) {
            int sequenceWidth = BUTTON_WIDTH;

            int carriageSpacing = 5;
            int carriageWidth = 80;
            int totalWidth = currentCarriages.size() * (carriageWidth + carriageSpacing) - carriageSpacing;

            if (totalWidth > sequenceWidth) {
                int mouseDelta = (int)mouseX - dragStartX;
                int scrollableWidth = totalWidth - sequenceWidth;
                double scrollRatio = (double)mouseDelta / sequenceWidth;

                horizontalScrollOffset = dragStartHorizontalScrollOffset + (int)(scrollRatio * scrollableWidth);
                horizontalScrollOffset = Math.max(0, Math.min(horizontalScrollOffset, scrollableWidth));
            }

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollbar = false;
            isDraggingHorizontalScrollbar = false;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int guiY = getGuiY();

        int contentY = guiY + CONTENT_PADDING;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;
        int contentHeight = GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;

        int listY;
        if (isTrainConfigTab) {
            if (isTrainConfigurationView) {
                listY = contentY + 24 + BUTTON_SPACING;
            } else {
                listY = contentY + 24 + BUTTON_SPACING + 24 + BUTTON_SPACING;
            }
        } else {
            listY = contentY + SEARCH_HEIGHT + BUTTON_SPACING;
        }

        int listHeight = contentHeight - (listY - contentY) - BUTTON_SPACING;

        if (mouseX >= lastListX && mouseX <= lastListX + lastListWidth && mouseY >= lastListY && mouseY <= lastListY + lastListHeight) {
            int totalHeight = calculateTotalHeight(Math.min(BUTTON_WIDTH, contentWidth));
            if (totalHeight > listHeight) {
                scrollOffset -= (int)(verticalAmount * 20);
                scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - listHeight));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int calculateTotalHeight(int areaWidth) {
        if (isTrainConfigTab) {
            if (isTrainConfigurationView) {
                int itemWidth = 90;
                int itemHeight = 24;
                int gap = 6;
                int columns = Math.max(1, (Math.min(BUTTON_WIDTH, areaWidth) + gap) / (itemWidth + gap));
                int count = Math.max(1, currentCarriages.size());
                int rows = (int)Math.ceil((double)count / columns);
                return rows * (itemHeight + gap) - gap;
        } else {
                List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
                int size = items != null ? items.size() : 0;
                return size > 0 ? size * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING : 0;
            }
        } else {
            List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
            int totalHeight = 0;
            if (items != null) {
                for (String trackId : items) {
                    Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
                    boolean isTitleOnly = false;

                    if (trackTypes != null) {
                        TrackRenderer.TrackTypeData trackData = trackTypes.get(trackId);
                        if (trackData != null) {
                            boolean hasIcon = trackData.icon() != null;
                            boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();
                            isTitleOnly = !hasIcon && !hasDesc;
                        }
                    }

                    int buttonHeight = isTitleOnly ? 25 : BUTTON_HEIGHT;
                    totalHeight += buttonHeight + BUTTON_SPACING;
                }
            }

            if (totalHeight > 0) {
                totalHeight -= BUTTON_SPACING;
            }

            return totalHeight;
        }
    }

    private void updateHoveredButton(int mouseX, int mouseY, int listX, int listY) {
        hoveredButtonIndex = -1;
        int currentY = listY;

        if (isTrainConfigTab && isTrainConfigurationView) {
            return;
        }

        if (!(mouseX >= this.lastListX && mouseX <= this.lastListX + this.lastListWidth && mouseY >= this.lastListY && mouseY <= this.lastListY + this.lastListHeight)) {
            return;
        }

        List<String> items = isTrainConfigTab ? (filteredTrainIds != null ? filteredTrainIds : availableTrainIds) : (filteredTrackIds != null ? filteredTrackIds : availableTrackIds);
        for (int i = 0; i < items.size(); i++) {
            int buttonHeight = getButtonHeight(i);

            int buttonY = currentY - scrollOffset;
            if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                hoveredButtonIndex = i;
                break;
            }

            currentY += buttonHeight + BUTTON_SPACING;
        }
    }

    private int getButtonHeight(int i) {
        int buttonHeight = BUTTON_HEIGHT;
        if (!isTrainConfigTab) {
            List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
            String trackId = items.get(i);
            Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
            boolean isTitleOnly = false;

            if (trackTypes != null) {
                TrackRenderer.TrackTypeData trackData = trackTypes.get(trackId);
                if (trackData != null) {
                    boolean hasIcon = trackData.icon() != null;
                    boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();
                    isTitleOnly = !hasIcon && !hasDesc;
                }
            }

            buttonHeight = isTitleOnly ? 25 : BUTTON_HEIGHT;
        }

        return buttonHeight;
    }

    private void renderTrackTypeButton(DrawContext context, int buttonIndex, int x, int y, int buttonHeight) {
        boolean isHovered = buttonIndex == hoveredButtonIndex;
        List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
        boolean isSelected = items.get(buttonIndex).equals(selectedTrackId);
        boolean isActive = !isSelected;

        renderButtonBackground(context, x, y, isHovered, isSelected, isActive, buttonHeight);
        renderTrackButtonContent(context, buttonIndex, x, y, buttonHeight);
    }

    private void renderTrainTypeButton(DrawContext context, int buttonIndex, int x, int y, int mouseX, int mouseY, float delta) {
        boolean isHovered = buttonIndex == hoveredButtonIndex;
        List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
        String trainId = items.get(buttonIndex);
        boolean isInCurrentConfig = currentCarriages.contains(trainId);

        int itemButtonWidth = BUTTON_WIDTH - 35;
        renderButtonBackground(context, x, y, isHovered, isInCurrentConfig, true, BUTTON_HEIGHT, itemButtonWidth);
        renderTrainButtonContent(context, buttonIndex, x, y, mouseX, mouseY, delta);
    }

    private void renderButtonBackground(DrawContext context, int x, int y, boolean isHovered, boolean isSelected, boolean isActive, int buttonHeight) {
        renderButtonBackground(context, x, y, isHovered, isSelected, isActive, buttonHeight, BUTTON_WIDTH);
    }

    private void renderButtonBackground(DrawContext context, int x, int y, boolean isHovered, boolean isSelected, boolean isActive, int buttonHeight, int buttonWidth) {
        ButtonTextures textures = new ButtonTextures(
            Identifier.ofVanilla("widget/button"),
            Identifier.ofVanilla("widget/button_disabled"),
            Identifier.ofVanilla("widget/button_highlighted")
        );

        Identifier textureId;
        if (!isActive) {
            textureId = textures.get(false, false);
        } else if (isHovered || isSelected) {
            textureId = textures.get(true, true);
        } else {
            textureId = textures.get(true, false);
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, buttonWidth, buttonHeight);
    }

    private void renderTrackButtonContent(DrawContext context, int buttonIndex, int x, int y, int buttonHeight) {
        List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
        if (buttonIndex >= items.size()) {
            return;
        }

        String trackId = items.get(buttonIndex);
        Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
        if (trackTypes == null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(trackId), x + 6, y + buttonHeight / 2 - 4, 0xFFFFFFFF);
            return;
        }

        TrackRenderer.TrackTypeData trackData = trackTypes.get(trackId);
        if (trackData == null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(trackId), x + 6, y + buttonHeight / 2 - 4, 0xFFFFFFFF);
            return;
        }

        int iconSize = 20;
        int padding = 8;
        int contentWidth = BUTTON_WIDTH - padding * 2;
        int textX = x + padding;
        int buttonCenterY = y + buttonHeight / 2;

        boolean hasIcon = trackData.icon() != null;
        boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();

        if (hasIcon) {
            int iconY = buttonCenterY - iconSize / 2;
            try {
                Identifier iconId = Identifier.of(trackData.icon());
                context.fill(x + padding - 1, iconY - 1, x + padding + iconSize + 1, iconY + iconSize + 1, 0xFFAAAAAA);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, iconId, x + padding, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            } catch (Exception e) {
                TrainModClient.LOGGER.error("Failed to render track icon: {}", trackData.icon(), e);
            }

            textX += iconSize + padding;
        }

        String title = trackData.name();
        String description = hasDesc ? trackData.description() : "";
        int titleColor = 0xFFFFFFFF;
        int descColor = 0xFFAAAAAA;

        if (hasDesc) {
            int titleY = y + padding + 2;
            if (hasIcon) {
                titleY = buttonCenterY - 10;
            }

            context.drawTextWithShadow(this.textRenderer, Text.literal(title), textX, titleY, titleColor);

            int descY = titleY + 12;
            int maxDescWidth = contentWidth - (hasIcon ? iconSize + padding : 0);
            List<String> wrappedLines = wrapText(description, maxDescWidth);
            for (String line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(line), textX, descY, descColor);
                descY += 9;
            }
        } else {
            int titleY = buttonCenterY - 4;
            context.drawTextWithShadow(this.textRenderer, Text.literal(title), textX, titleY, titleColor);
        }
    }

    private void renderTrainButtonContent(DrawContext context, int buttonIndex, int x, int y, int mouseX, int mouseY, float delta) {
        List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
        if (buttonIndex >= items.size()) {
            return;
        }

        String trainId = items.get(buttonIndex);
        TrainConfigLoader.TrainTypeData trainData = TrainConfigLoader.getTrainType(trainId);
        if (trainData == null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(trainId), x + 6, y + BUTTON_HEIGHT / 2 - 4, 0xFFFFFFFF);
            return;
        }

        int iconSize = 20;
        int padding = 8;
        int contentWidth = BUTTON_WIDTH - padding * 2 - 45;
        int textX = x + padding;
        int buttonCenterY = y + BUTTON_HEIGHT / 2;

        boolean hasIcon = trainData.icon() != null && !trainData.icon().isEmpty();
        boolean hasDesc = trainData.description() != null && !trainData.description().isEmpty();

        if (hasIcon) {
            int iconY = buttonCenterY - iconSize / 2;
            try {
                Identifier iconId = Identifier.of(trainData.icon());
                context.fill(x + padding - 1, iconY - 1, x + padding + iconSize + 1, iconY + iconSize + 1, 0xFFAAAAAA);
                context.drawTexture(RenderPipelines.GUI_TEXTURED, iconId, x + padding, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
            } catch (Exception e) {
                TrainModClient.LOGGER.error("Failed to render train icon: {}", trainData.icon(), e);
            }

            textX += iconSize + padding;
        }

        String title = trainData.name();
        String description = hasDesc ? trainData.description() : "";
        int titleColor = 0xFFFFFFFF;
        int descColor = 0xFFAAAAAA;

        if (hasDesc) {
            int titleY = buttonCenterY - 10;
            context.drawTextWithShadow(this.textRenderer, Text.literal(title), textX, titleY, titleColor);

            int descY = titleY + 12;
            int maxDescWidth = contentWidth - (hasIcon ? iconSize + padding : 0);
            List<String> wrappedLines = wrapText(description, maxDescWidth);

            for (String line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(line), textX, descY, descColor);
                descY += 9;
            }
        } else {
            int titleY = buttonCenterY - 4;
            context.drawTextWithShadow(this.textRenderer, Text.literal(title), textX, titleY, titleColor);
        }

        renderAddButton(context, x, y, mouseX, mouseY);
    }

    private void renderAddButton(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int addButtonWidth = 25;
        int addButtonHeight = BUTTON_HEIGHT;
        int addButtonX = x + BUTTON_WIDTH - 30;

        boolean isHovered = isAddButtonHovered(addButtonX, y, addButtonWidth, addButtonHeight, mouseX, mouseY);

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, isHovered ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, addButtonX, y, addButtonWidth, addButtonHeight);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+"), addButtonX + addButtonWidth/2, y + addButtonHeight/2 - 4, 0xFFFFFFFF);
    }

    private boolean isAddButtonHovered(int addX, int addY, int width, int height, int mouseX, int mouseY) {
        return mouseX >= addX && mouseX <= addX + width && mouseY >= addY && mouseY <= addY + height;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + (!currentLine.isEmpty() ? " " : "") + word;
            if (this.textRenderer.getWidth(testLine) <= maxWidth) {
                currentLine.append(!currentLine.isEmpty() ? " " : "").append(word);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private void saveAndClose() {
        try {
            dwellTimeSeconds = Integer.parseInt(dwellTimeField.getText());
        } catch (NumberFormatException e) {
            TrainModClient.LOGGER.warn("Invalid dwell time input, using default: {}", dwellTimeField.getText(), e);
            dwellTimeSeconds = segment.getDwellTimeSeconds();
        }

        double curvature;
        try {
            curvature = Double.parseDouble(slopeCurvatureField.getText());
        } catch (Exception e) {
            TrainModClient.LOGGER.warn("Failed to parse curvature value, using default: {}", slopeCurvatureField.getText(), e);
            curvature = segment.getSlopeCurvature();
        }

        UpdateTrackSegmentPayload trackPayload = new UpdateTrackSegmentPayload(
            segment.start(),
            segment.end(),
            selectedTrackId,
            selectedType,
            dwellTimeSeconds,
            curvature,
            null
        );

        TrainModClientNetworking.sendToServer(trackPayload);
        if (isTrainConfigTab && "siding".equals(selectedType)) {
            String trackSegmentKey = segment.start().getX() + "," + segment.start().getY() + "," + segment.start().getZ() + "->" + segment.end().getX() + "," + segment.end().getY() + "," + segment.end().getZ();

            List<Double> carriageLengths = new ArrayList<>();
            List<Double> bogieInsets = new ArrayList<>();
            List<Double> boundingBoxWidths = new ArrayList<>();
            List<Double> boundingBoxLengths = new ArrayList<>();
            List<Double> boundingBoxHeights = new ArrayList<>();

            final double SPACING_BUFFER = 1.0;
            for (String cid : currentCarriages) {
                var td = TrainConfigLoader.getTrainType(cid);
                double base = td != null ? td.length() : 25.0;
                double spacing = base + SPACING_BUFFER;

                carriageLengths.add(spacing);

                double inset = td != null ? td.bogieInset() : 0.1;
                bogieInsets.add(inset);

                double width = td != null ? td.width() : 1.0;
                double height = td != null ? td.height() : 1.0;
                boundingBoxWidths.add(width);
                boundingBoxLengths.add(base);
                boundingBoxHeights.add(height);
            }

            UpdateTrainConfigPayload trainPayload = new UpdateTrainConfigPayload(trainId, currentCarriages, carriageLengths, bogieInsets, trackSegmentKey, boundingBoxWidths, boundingBoxLengths, boundingBoxHeights);
            TrainModClientNetworking.sendToServer(trainPayload);
        }

        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            saveAndClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        saveAndClose();
    }

    private void closeWithoutSaving() {
        super.close();
    }
}
