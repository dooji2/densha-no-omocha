package com.dooji.dno.gui;

import com.dooji.dno.TrainModClient;
import com.dooji.dno.network.TrainModClientNetworking;
import com.dooji.dno.network.payloads.SyncRoutesPayload;
import com.dooji.dno.network.payloads.UpdateTrackSegmentPayload;
import com.dooji.dno.network.payloads.UpdateTrainConfigPayload;
import com.dooji.dno.network.payloads.GeneratePathPayload;
import com.dooji.dno.network.payloads.RefreshTrainPathPayload;
import com.dooji.dno.track.TrackRenderer;
import com.dooji.dno.track.TrackSegment;
import com.dooji.dno.track.Route;
import com.dooji.dno.track.RouteManagerClient;
import com.dooji.dno.track.TrackManagerClient;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class should not be this big. It's a mess. It will be refactored in the future, but for now it'll have to do because
 * I'm not willing to spend more time on it than I already have, but maybe after the event or after the testing phase.
 */
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
    private ButtonWidget sidebarRouteButton;
    private ButtonWidget trainModeListButton;
    private ButtonWidget trainModeConfigButton;
    private ButtonWidget routeModeListButton;
    private ButtonWidget routeModeConfigButton;
    private ButtonWidget refreshPathButton;
    private TextFieldWidget dwellTimeField;
    private TextFieldWidget slopeCurvatureField;
    private TextFieldWidget maxSpeedField;
    private TextFieldWidget stationNameField;
    private TextFieldWidget routeNameField;
    private int hoveredButtonIndex = -1;

    private String selectedTrackId;
    private String selectedType;
    private boolean isTrainConfigTab = false;
    private boolean isRouteTab = false;
    private boolean wasRouteTabJustActivated = false;
    private boolean isRouteConfigurationView = false;
    private boolean isTrainConfigurationView = false;
    private int dwellTimeSeconds;

    private List<String> availableTrackIds;
    private List<String> availableTrainIds;
    private List<String> availableTypes;
    private List<String> currentCarriages;
    private List<String> availableStationIds;
    private List<String> availableRoutes;
    private String selectedRouteId;
    private List<String> currentRouteStations;
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

    private ButtonWidget createRouteButton;
    private ButtonWidget backToRouteListButton;

    private static final Identifier BUTTON_NORMAL = Identifier.ofVanilla("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.ofVanilla("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.ofVanilla("widget/button_disabled");

    private boolean routeCreationShowSelected = false;
    private ButtonWidget routeCreateTabAvailableButton;
    private ButtonWidget routeCreateTabSelectedButton;

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
            isRouteTab = false;
            isTrainConfigurationView = false;
            scrollOffset = 0;
            loadCurrentTrainConfig();
            updateTrainConfigTabVisibility();
        }).dimensions(sidebarX, sidebarY + sidebarButtonHeight + BUTTON_SPACING, sidebarButtonWidth, sidebarButtonHeight).build();
        this.addDrawableChild(this.sidebarTrainButton);

        this.sidebarRouteButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.tab.route"), button -> {
            isTrainConfigTab = false;
            isRouteTab = true;
            isTrainConfigurationView = false;
            scrollOffset = 0;
            wasRouteTabJustActivated = true;

            requestRouteSync();
            loadRouteData();
            refreshRouteList();
            updateTrainConfigTabVisibility();
        }).dimensions(sidebarX, sidebarY + (sidebarButtonHeight + BUTTON_SPACING) * 2, sidebarButtonWidth, sidebarButtonHeight).build();
        this.addDrawableChild(this.sidebarRouteButton);

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

        this.routeModeListButton = ButtonWidget.builder(Text.translatable("gui.dno.train_config.mode.list"), button -> {
            isRouteConfigurationView = false;
            scrollOffset = 0;
            updateTrainConfigTabVisibility();
        }).dimensions(contentX, contentY, 50, 20).build();
        this.addDrawableChild(this.routeModeListButton);

        this.routeModeConfigButton = ButtonWidget.builder(Text.literal("+"), button -> {
            isRouteConfigurationView = true;
            scrollOffset = 0;
            if (this.currentRouteStations == null || this.currentRouteStations.isEmpty()) {
                this.currentRouteStations = new ArrayList<>();
            }
            updateTrainConfigTabVisibility();
        }).dimensions(contentX + 55, contentY, 20, 20).build();
        this.addDrawableChild(this.routeModeConfigButton);

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

        int fieldWidth = 100;
        int fieldHeight = 20;
        
        this.maxSpeedField = new TextFieldWidget(this.textRenderer, 0, 0, fieldWidth, fieldHeight, Text.translatable("gui.dno.track_config.max_speed"));
        this.maxSpeedField.setText(String.valueOf(segment.getMaxSpeedKmh()));
        this.addDrawableChild(this.maxSpeedField);
        
        this.stationNameField = new TextFieldWidget(this.textRenderer, 0, 0, fieldWidth, fieldHeight, Text.translatable("gui.dno.track_config.station_name"));
        this.stationNameField.setText(segment.getStationName());
        this.addDrawableChild(this.stationNameField);
        
        this.dwellTimeField = new TextFieldWidget(this.textRenderer, 0, 0, fieldWidth, fieldHeight, Text.translatable("gui.dno.track_config.dwell_time"));
        this.dwellTimeField.setText(String.valueOf(dwellTimeSeconds));
        this.addDrawableChild(this.dwellTimeField);

        this.slopeCurvatureField = new TextFieldWidget(this.textRenderer, 0, 0, fieldWidth, fieldHeight, Text.translatable("gui.dno.track_config.curvature"));
        this.slopeCurvatureField.setText(String.valueOf(segment.getSlopeCurvature()));
        this.addDrawableChild(this.slopeCurvatureField);

        this.routeNameField = new TextFieldWidget(this.textRenderer, contentX, contentY + GUI_HEIGHT - FOOTER_HEIGHT - 55, fieldWidth, fieldHeight, Text.translatable("gui.dno.create_route.name"));
        this.routeNameField.setText("Route");
        this.addDrawableChild(this.routeNameField);

        int footerY = guiY + GUI_HEIGHT - FOOTER_HEIGHT + (FOOTER_HEIGHT - 20) / 2;
        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("gui.dno.common.save"), button -> saveAndClose()).dimensions(contentX, footerY, 80, 20).build();
        this.addDrawableChild(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.dno.common.cancel"), button -> closeWithoutSaving()).dimensions(contentX + 85, footerY, 80, 20).build();
        this.addDrawableChild(cancelButton);

        this.createRouteButton = ButtonWidget.builder(Text.translatable("gui.dno.create_route.create"), button -> {
            createNewRoute();
        }).dimensions(contentX, contentY + GUI_HEIGHT - FOOTER_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.createRouteButton);

        this.backToRouteListButton = ButtonWidget.builder(Text.translatable("gui.dno.common.back"), button -> {
            isRouteConfigurationView = false;
            updateTrainConfigTabVisibility();
        }).dimensions(contentX + 85, contentY + GUI_HEIGHT - FOOTER_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.backToRouteListButton);

        this.routeCreateTabAvailableButton = ButtonWidget.builder(Text.translatable("gui.dno.create_route.available_stations"), button -> {
            routeCreationShowSelected = false;
            updateTrainConfigTabVisibility();
        }).dimensions(contentX, contentY, 100, 20).build();
        this.addDrawableChild(this.routeCreateTabAvailableButton);

        this.routeCreateTabSelectedButton = ButtonWidget.builder(Text.translatable("gui.dno.create_route.selected_stations"), button -> {
            routeCreationShowSelected = true;
            updateTrainConfigTabVisibility();
        }).dimensions(contentX + 105, contentY, 100, 20).build();
        this.addDrawableChild(this.routeCreateTabSelectedButton);

        this.refreshPathButton = ButtonWidget.builder(Text.translatable("gui.dno.track_config.refresh_path"), button -> {
            if (segment.getRouteId() != null && !segment.getRouteId().trim().isEmpty()) {
                generateAndSendPath(segment.getRouteId());
            } else {
                var payload = new RefreshTrainPathPayload(segment.getTrainId(), segment.start(), segment.end());
                TrainModClientNetworking.sendToServer(payload);
            }
        }).dimensions(contentX + 150, contentY, 120, 20).build();
        this.addDrawableChild(this.refreshPathButton);

        updateFilters();
        if (!isRouteTab) {
            updateTextFieldPositions();
        }
        updateTrainConfigTabVisibility();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (!isTrainConfigTab && !isRouteTab) {
            updateTextFieldPositions();
        }

        if (isRouteTab && this.routeNameField != null && this.routeNameField.visible) {
            int centerX = this.width / 2;
            int guiX = centerX - GUI_WIDTH / 2;
            int guiY = getGuiY();
            int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
            int contentY = guiY + CONTENT_PADDING;
            int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;
            
            this.routeNameField.setX(contentX);
            this.routeNameField.setY(contentY + GUI_HEIGHT - FOOTER_HEIGHT - 55);
            this.routeNameField.setWidth(contentWidth);
        }
    }

    private int routeSyncTicks = 0;
    private static final int ROUTE_SYNC_INTERVAL = 60;
    
    @Override
    public void tick() {
        super.tick();
        
        if (isRouteTab) {
            routeSyncTicks++;
            if (routeSyncTicks >= ROUTE_SYNC_INTERVAL) {
                routeSyncTicks = 0;
                requestRouteSync();
            }
        } else {
            routeSyncTicks = 0;
        }
    }
    
    private void requestRouteSync() {
        var payload = new SyncRoutesPayload(new HashMap<>());
        TrainModClientNetworking.sendToServer(payload);
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

    public void loadRouteData() {
        String currentSelectedRoute = selectedRouteId;
        int currentScrollOffset = scrollOffset;
        
        availableStationIds = new ArrayList<>();
        availableRoutes = new ArrayList<>();
        
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(MinecraftClient.getInstance().world);
        for (TrackSegment segment : tracks.values()) {
            if ("platform".equals(segment.getType()) && segment.getStationId() != null && !segment.getStationId().trim().isEmpty()) {
                if (!availableStationIds.contains(segment.getStationId())) {
                    availableStationIds.add(segment.getStationId());
                }
            }
        }
        
        List<Route> routes = RouteManagerClient.getAllRoutes();
        for (Route route : routes) {
            availableRoutes.add(route.getRouteId());
        }
        
        if (currentSelectedRoute != null && availableRoutes.contains(currentSelectedRoute)) {
            selectedRouteId = currentSelectedRoute;
        }
        if (currentScrollOffset > 0) {
            scrollOffset = currentScrollOffset;
        }
    }

    public void refreshRouteList() {
        loadRouteData();
    }

    private void updateTrainConfigTabVisibility() {
        boolean isSiding = "siding".equals(selectedType);
        boolean isPlatform = "platform".equals(selectedType);
        if (this.sidebarTrainButton != null) {
            this.sidebarTrainButton.active = isSiding;
        }

        if (this.sidebarRouteButton != null) {
            this.sidebarRouteButton.active = isSiding;
        }

        boolean trackView = !isTrainConfigTab && !isRouteTab;
        boolean routeView = isRouteTab;
        this.segmentTypeButton.visible = trackView;
        this.maxSpeedField.visible = trackView;
        this.stationNameField.visible = isPlatform && trackView;
        this.dwellTimeField.visible = isPlatform && trackView;
        boolean isSlope = segment.start().getY() != segment.end().getY();
        this.slopeCurvatureField.visible = isSlope && trackView;
        
        if (trackView) {
            updateTextFieldPositions();
        }

        if (!isSiding && isTrainConfigTab) {
            isTrainConfigTab = false;
        }

        if (!isSiding && isRouteTab) {
            isRouteTab = false;
        }

        boolean trainView = isTrainConfigTab;
        if (this.trainModeListButton != null) {
            this.trainModeListButton.visible = trainView;
        }

        if (this.trainModeConfigButton != null) {
            this.trainModeConfigButton.visible = trainView;
        }

        if (this.routeModeListButton != null) {
            this.routeModeListButton.visible = routeView && !isRouteConfigurationView;
        }

        if (this.routeModeConfigButton != null) {
            this.routeModeConfigButton.visible = routeView && !isRouteConfigurationView;
        }

        if (this.routeCreateTabAvailableButton != null) {
            this.routeCreateTabAvailableButton.visible = routeView && isRouteConfigurationView;
        }

        if (this.routeCreateTabSelectedButton != null) {
            this.routeCreateTabSelectedButton.visible = routeView && isRouteConfigurationView;
        }

        if (this.createRouteButton != null) {
            this.createRouteButton.visible = routeView && isRouteConfigurationView;
        }

        if (this.backToRouteListButton != null) {
            this.backToRouteListButton.visible = routeView && isRouteConfigurationView;
        }

        if (this.routeNameField != null) {
            this.routeNameField.visible = routeView && isRouteConfigurationView;
            if (this.routeNameField.visible) {
                int centerX = this.width / 2;
                int guiX = centerX - GUI_WIDTH / 2;
                int guiY = getGuiY();
                int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
                int contentY = guiY + CONTENT_PADDING;
                int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - CONTENT_PADDING * 3;
                
                this.routeNameField.setX(contentX);
                this.routeNameField.setY(contentY + GUI_HEIGHT - FOOTER_HEIGHT - 55);
                this.routeNameField.setWidth(contentWidth);
            }
        }

        if (this.refreshPathButton != null) {
            this.refreshPathButton.visible = isTrainConfigTab && isTrainConfigurationView;
        }

        if (this.trackSearchField != null) {
            this.trackSearchField.visible = trackView;
        }

        if (this.trainSearchField != null) {
            this.trainSearchField.visible = trainView && !isTrainConfigurationView;
        }

        updateFilters();
    }
    
    private void updateTextFieldPositions() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int guiX = centerX - GUI_WIDTH / 2;
        int guiY = centerY - GUI_HEIGHT / 2;
        int contentX = guiX + SIDEBAR_WIDTH + CONTENT_PADDING * 2;
        int typeButtonY = guiY + CONTENT_PADDING + 60;
        int fieldsY = typeButtonY + 25;
        
        int fieldWidth = 100;
        int fieldHeight = 20;
        int fieldSpacing = 10;
        
        List<TextFieldWidget> visibleFields = new ArrayList<>();
        if (this.maxSpeedField.visible) visibleFields.add(this.maxSpeedField);
        if (this.stationNameField.visible) visibleFields.add(this.stationNameField);
        if (this.dwellTimeField.visible) visibleFields.add(this.dwellTimeField);
        if (this.slopeCurvatureField.visible) visibleFields.add(this.slopeCurvatureField);
        
        int fieldsPerRow = 2;
        int currentRow = 0;
        int currentCol = 0;
        
        for (TextFieldWidget field : visibleFields) {
            int x = contentX + (currentCol * (fieldWidth + fieldSpacing));
            int y = fieldsY + (currentRow * (fieldHeight + fieldSpacing));
            field.setPosition(x, y);
            
            currentCol++;
            if (currentCol >= fieldsPerRow) {
                currentCol = 0;
                currentRow++;
            }
        }
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
            listY = contentY + 24 + BUTTON_SPACING;
        }

        int listWidth = Math.min(BUTTON_WIDTH, contentWidth);
        int listHeight = 40;
        if (!isTrainConfigTab && !isRouteTab) {
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

            int fieldsY = typeButtonY - 25;
            int fieldWidth = 100;
            int fieldHeight = 20;
            int fieldSpacing = 10;
            
            if (this.maxSpeedField != null) {
                this.maxSpeedField.setX(contentX);
                this.maxSpeedField.setY(fieldsY);
                this.maxSpeedField.visible = true;
            }
            
            if (this.stationNameField != null) {
                this.stationNameField.setX(contentX + fieldWidth + fieldSpacing);
                this.stationNameField.setY(fieldsY);
                this.stationNameField.visible = isPlatform;
            }
            
            int secondRowY = fieldsY - fieldHeight - fieldSpacing;
            
            if (isPlatform && this.dwellTimeField != null) {
                this.dwellTimeField.setX(contentX);
                this.dwellTimeField.setY(secondRowY);
                this.dwellTimeField.visible = true;
            }

            if (isSlope && this.slopeCurvatureField != null) {
                this.slopeCurvatureField.setX(contentX + fieldWidth + fieldSpacing);
                this.slopeCurvatureField.setY(secondRowY);
                this.slopeCurvatureField.visible = true;
            }

            int nextY;
            if (isPlatform || isSlope) {
                nextY = secondRowY - (12 + BUTTON_SPACING);
            } else {
                nextY = fieldsY;
            }

            listHeight = Math.max(40, nextY - listY);
        } else if (isTrainConfigTab) {
            int contentHeight = GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;
            listHeight = contentHeight - (listY - contentY) - BUTTON_SPACING;

            if (listHeight < 40) listHeight = 40;
        } else if (isRouteTab) {
            int contentHeight = GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING * 2;
            listHeight = contentHeight - (listY - contentY) - BUTTON_SPACING;

            if (listHeight < 40) listHeight = 40;
            
            if (isRouteConfigurationView) {
                int routeNameFieldY = contentY + GUI_HEIGHT - FOOTER_HEIGHT - 55;
                int availableHeight = (routeNameFieldY - BUTTON_SPACING) - listY;
                if (availableHeight > 0) {
                    listHeight = Math.min(listHeight, availableHeight);
                }
            } else {
                int bottomY = guiY + GUI_HEIGHT - FOOTER_HEIGHT - CONTENT_PADDING;
                int availableHeight = bottomY - listY - BUTTON_SPACING;
                if (availableHeight > 0) {
                    listHeight = Math.min(listHeight, availableHeight);
                }
            }
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
        } else if (isRouteTab) {
            if (wasRouteTabJustActivated) {
                wasRouteTabJustActivated = false;
                refreshRouteList();
            }

            if (isRouteConfigurationView) {
                renderRouteCreationView(context, listX, listY, listWidth, listHeight, mouseX, mouseY, delta);
            } else {
                renderRouteList(context, listX, listY, listWidth, listHeight, mouseX, mouseY, delta);
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

            if (buttonY + buttonHeight >= listY && buttonY <= listY + lastListHeight) {
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

            if (buttonY + BUTTON_HEIGHT >= listY && buttonY <= listY + lastListHeight) {
                renderTrainTypeButton(context, i, listX, buttonY, mouseX, mouseY, delta);
            }

            currentY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    private void renderRouteList(DrawContext context, int listX, int listY, int listWidth, int listHeight, int mouseX, int mouseY, float delta) {
        int currentY = listY;
        
        context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.route.available_routes"), listX, listY - 20, 0xFFFFFFFF);
        
        List<String> routes = availableRoutes != null ? availableRoutes : new ArrayList<>();
        int routeItemHeight = 25;
        
        for (int i = 0; i < routes.size(); i++) {
            String routeId = routes.get(i);
            int buttonY = currentY - scrollOffset;

            if (buttonY + routeItemHeight >= listY && buttonY <= listY + listHeight) {
                boolean isSelected = routeId.equals(selectedRouteId);
                renderRouteButton(context, i, listX, buttonY, routeId, isSelected);
            }

            currentY += routeItemHeight + BUTTON_SPACING;
        }
        
        if (routes.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("gui.dno.route.no_routes"), listX, listY, 0xFF888888);
        }
    }

    private void renderRouteCreationView(DrawContext context, int listX, int listY, int listWidth, int listHeight, int mouseX, int mouseY, float delta) {
        if (routeCreationShowSelected) {
            renderSelectedStationsForRoute(context, listX, listY, listWidth, listHeight, mouseX, mouseY, delta);
        } else {
            renderAvailableStationsForRoute(context, listX, listY, listWidth, listHeight, mouseX, mouseY, delta);
        }
    }

    private void renderAvailableStationsForRoute(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        int currentY = y - scrollOffset;
        for (String stationId : availableStationIds) {
            if (currentY + 25 >= y && currentY <= y + height) {
                boolean isSelected = currentRouteStations != null && currentRouteStations.contains(stationId);
                renderStationButtonForRoute(context, x, currentY, stationId, isSelected, mouseX, mouseY);
            }
            currentY += 25 + BUTTON_SPACING;
        }
    }

    private void renderStationButtonForRoute(DrawContext context, int x, int y, String stationId, boolean isSelected, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + BUTTON_WIDTH && mouseY >= y && mouseY <= y + 25;
        boolean isActive = true;

        renderButtonBackground(context, x, y, isHovered, isSelected, isActive, 25, BUTTON_WIDTH - 35);
        
        String displayName = getStationDisplayName(stationId);
        int textStartX = x + 6;
        int textEndX = x + BUTTON_WIDTH - 35;
        
        renderScrollableText(context, displayName, textStartX, y, textEndX, y + 25, 0xFFFFFFFF);
        
        int addButtonX = x + BUTTON_WIDTH - 30;
        int addButtonY = y;
        int addButtonHeight = 25;
        
        boolean addButtonHovered = mouseX >= addButtonX && mouseX <= addButtonX + 25 && mouseY >= addButtonY && mouseY <= addButtonY + addButtonHeight;
        Identifier addButtonTex = addButtonHovered ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, addButtonTex, addButtonX, addButtonY, 25, addButtonHeight);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+"), addButtonX + 25 / 2, addButtonY + 25 / 2 - 4, 0xFFFFFFFF);
    }

    private String getStationDisplayName(String stationId) {
        Map<String, TrackSegment> tracks = TrackManagerClient.getTracksFor(MinecraftClient.getInstance().world);
        for (TrackSegment segment : tracks.values()) {
            if (segment.getStationId() != null && segment.getStationId().equals(stationId)) {
                String name = segment.getStationName();
                return (name != null && !name.trim().isEmpty()) ? name : stationId;
            }
        }
        return stationId;
    }

    private void renderSelectedStationsForRoute(DrawContext context, int startX, int startY, int areaWidth, int areaHeight, int mouseX, int mouseY, float delta) {
        int itemWidth = 90;
        int itemHeight = 24;
        int gap = 6;
        int columns = Math.max(1, (areaWidth + gap) / (itemWidth + gap));
        int yBase = startY - scrollOffset;

        if (currentRouteStations != null) {
            for (int i = 0; i < currentRouteStations.size(); i++) {
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

                String stationId = currentRouteStations.get(i);
                String displayName = getStationDisplayName(stationId);
                context.enableScissor(bx, by, bx + itemWidth, by + itemHeight);
                context.drawTextWithShadow(this.textRenderer, Text.literal(displayName), bx + 6, by + 7, 0xFFFFFFFF);
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
        }

        if (currentRouteStations != null && !currentRouteStations.isEmpty()) {
            int frontIndex = 0;
            int backIndex = currentRouteStations.size() - 1;

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

    private String getRouteDisplayName(String routeId) {
        if (RouteManagerClient.routeExists(routeId)) {
            var route = RouteManagerClient.getRoute(routeId);
            return route != null ? route.getDisplayName() : routeId;
        }

        return routeId;
    }

    private void renderRouteButton(DrawContext context, int buttonIndex, int x, int y, String routeId, boolean isSelected) {
        boolean isHovered = buttonIndex == hoveredButtonIndex;
        boolean isCurrentlyAssigned = segment.getRouteId() != null && segment.getRouteId().equals(routeId);
        boolean isActive = !isCurrentlyAssigned;

        int routeItemHeight = 25;
        renderButtonBackground(context, x, y, isHovered, isSelected, isActive, routeItemHeight);
        
        String routeName = getRouteDisplayName(routeId);
        int color = isCurrentlyAssigned ? 0xFF888888 : 0xFFFFFFFF;
        context.drawTextWithShadow(this.textRenderer, Text.literal(routeName), x + 6, y + routeItemHeight / 2 - 4, color);
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

            int textStartX = bx + 6;
            int textEndX = bx + itemWidth - 6;
            
            renderScrollableText(context, title, textStartX, by, textEndX, by + itemHeight, 0xFFFFFFFF);

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

    private void renderScrollableText(DrawContext context, String text, int startX, int startY, int endX, int endY, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        int availableWidth = endX - startX;
        
        if (textWidth <= availableWidth) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), startX, startY + (endY - startY - 8) / 2, color);
        } else {
            int scrollSpeed = 2000;
            int pauseDuration = 1000;
            int totalCycle = scrollSpeed + pauseDuration * 2;
            long time = System.currentTimeMillis();
            long cycleTime = time % totalCycle;
            
            int currentOffset;
            if (cycleTime < pauseDuration) {
                currentOffset = 0;
            } else if (cycleTime < pauseDuration + scrollSpeed) {
                double progress = (cycleTime - pauseDuration) / (double) scrollSpeed;
                int scrollDistance = textWidth - availableWidth;
                currentOffset = (int) (progress * scrollDistance);
            } else {
                currentOffset = textWidth - availableWidth;
            }
            
            context.enableScissor(startX, startY, endX, endY);
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), startX - currentOffset, startY + (endY - startY - 8) / 2, color);
            context.disableScissor();
        }
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
                } else if (isRouteTab) {
                    if (isRouteConfigurationView) {
                        handleRouteCreationClick(mouseX, mouseY, lastListX, lastListY);
                    } else {
                        handleRouteTabClick(mouseX, mouseY, lastListX, lastListY);
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

    private void handleRouteTabClick(double mouseX, double mouseY, int listX, int listY) {
        int currentY = listY;
        List<String> routes = availableRoutes != null ? availableRoutes : new ArrayList<>();
        int routeItemHeight = 25;
        for (int i = 0; i < routes.size(); i++) {
            String routeId = routes.get(i);
            int buttonY = currentY - scrollOffset;
            if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + routeItemHeight) {
                if (segment.getRouteId() != null && segment.getRouteId().equals(routeId)) {
                    break;
                }
                selectedRouteId = routeId;
                break;
            }
            currentY += routeItemHeight + BUTTON_SPACING;
        }
    }

    private void handleRouteCreationClick(double mouseX, double mouseY, int listX, int listY) {
        if (routeCreationShowSelected) {
            handleSelectedStationsClick(mouseX, mouseY, listX, listY);
        } else {
            handleAvailableStationsClick(mouseX, mouseY, listX, listY);
        }
    }

    private void handleAvailableStationsClick(double mouseX, double mouseY, int listX, int listY) {
        int currentY = listY - scrollOffset;
        for (String stationId : availableStationIds) {
            if (mouseX >= listX && mouseX <= listX + BUTTON_WIDTH && mouseY >= currentY && mouseY <= currentY + 25) {
                int addButtonX = listX + BUTTON_WIDTH - 30;
                int addButtonY = currentY;
                int addButtonHeight = 25;
                
                if (mouseX >= addButtonX && mouseX <= addButtonX + 25 && mouseY >= addButtonY && mouseY <= addButtonY + addButtonHeight) {
                    if (currentRouteStations == null) currentRouteStations = new ArrayList<>();
                    currentRouteStations.add(stationId);
                }
                break;
            }

            currentY += 25 + BUTTON_SPACING;
        }
    }

    private void handleSelectedStationsClick(double mouseX, double mouseY, int listX, int listY) {
        if (currentRouteStations == null) return;
        
        int itemWidth = 90;
        int itemHeight = 24;
        int gap = 6;
        int columns = Math.max(1, (BUTTON_WIDTH + gap) / (itemWidth + gap));
        int yBase = listY - scrollOffset;

        for (int i = 0; i < currentRouteStations.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int bx = listX + col * (itemWidth + gap);
            int by = yBase + row * (itemHeight + gap);

            if (mouseY >= by && mouseY <= by + itemHeight) {
                int controlSize = 16;
                int controlsWidth = controlSize * 3 + gap * 2;
                int cx = bx + (itemWidth - controlsWidth) / 2;

                if (mouseX >= cx && mouseX <= cx + controlSize) {
                    if (i > 0) {
                        String temp = currentRouteStations.get(i);
                        currentRouteStations.set(i, currentRouteStations.get(i - 1));
                        currentRouteStations.set(i - 1, temp);
                    }
                    break;
                } else if (mouseX >= cx + controlSize + gap && mouseX <= cx + controlSize + gap + controlSize) {
                    currentRouteStations.remove(i);
                    break;
                } else if (mouseX >= cx + (controlSize + gap) * 2 && mouseX <= cx + (controlSize + gap) * 2 + controlSize) {
                    if (i < currentRouteStations.size() - 1) {
                        String temp = currentRouteStations.get(i);
                        currentRouteStations.set(i, currentRouteStations.get(i + 1));
                        currentRouteStations.set(i + 1, temp);
                    }
                    break;
                }
            }
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
                    if (canAddCarriage(availableTrainId)) {
                        if (currentCarriages == null) currentCarriages = new ArrayList<>();
                        currentCarriages.add(availableTrainId);
                    }
                }

                break;
            }

            currentY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    private void createNewRoute() {
        if (currentRouteStations != null && !currentRouteStations.isEmpty()) {
            String routeName = routeNameField.getText();
            if (routeName == null || routeName.trim().isEmpty()) {
                routeName = "Route";
            }

            var payload = new com.dooji.dno.network.payloads.CreateRoutePayload(routeName, currentRouteStations);
            TrainModClientNetworking.sendToServer(payload);
            isRouteConfigurationView = false;
            currentRouteStations = new ArrayList<>();
            
            updateTrainConfigTabVisibility();
            refreshRouteList();
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
        if (mouseX >= lastListX && mouseX <= lastListX + lastListWidth && mouseY >= lastListY && mouseY <= lastListY + lastListHeight) {
            int totalHeight = calculateTotalHeight(lastListWidth);
            if (totalHeight > lastListHeight) {
                scrollOffset -= (int)(verticalAmount * 20);
                scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - lastListHeight + 1));
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
        } else if (isRouteTab) {
            if (isRouteConfigurationView) {
                if (routeCreationShowSelected) {
                    int itemWidth = 90;
                    int itemHeight = 24;
                    int gap = 6;
                    int columns = Math.max(1, (BUTTON_WIDTH + gap) / (itemWidth + gap));
                    int count = Math.max(1, currentRouteStations != null ? currentRouteStations.size() : 0);
                    int rows = (int)Math.ceil((double)count / columns);
                    return rows * (itemHeight + gap) - gap;
                } else {
                    int routeItemHeight = 25;
                    List<String> items = availableStationIds != null ? availableStationIds : new ArrayList<>();
                    int size = items != null ? items.size() : 0;
                    return size > 0 ? size * (routeItemHeight + BUTTON_SPACING) - BUTTON_SPACING : 0;
                }
            } else {
                int routeItemHeight = 25;
                List<String> items = availableRoutes != null ? availableRoutes : new ArrayList<>();
                int size = items != null ? items.size() : 0;
                return size > 0 ? size * (routeItemHeight + BUTTON_SPACING) - BUTTON_SPACING : 0;
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

        if (isTrainConfigTab) {
            List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
            for (int i = 0; i < items.size(); i++) {
                int buttonHeight = BUTTON_HEIGHT;
                int buttonY = currentY - scrollOffset;
                if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                    hoveredButtonIndex = i;
                    break;
                }
                currentY += buttonHeight + BUTTON_SPACING;
            }
        } else if (isRouteTab) {
            if (isRouteConfigurationView) {
                if (!routeCreationShowSelected) {
                    List<String> items = availableStationIds != null ? availableStationIds : new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        int buttonHeight = 25;
                        int buttonY = currentY - scrollOffset;
                        if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                            hoveredButtonIndex = i;
                            break;
                        }

                        currentY += buttonHeight + BUTTON_SPACING;
                    }
                }
            } else {
                List<String> items = availableRoutes != null ? availableRoutes : new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    int buttonHeight = 25;
                    int buttonY = currentY - scrollOffset;
                    if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                        hoveredButtonIndex = i;
                        break;
                    }

                    currentY += buttonHeight + BUTTON_SPACING;
                }
            }
        } else {
            List<String> items = filteredTrackIds != null ? filteredTrackIds : availableTrackIds;
            for (int i = 0; i < items.size(); i++) {
                Map<String, TrackRenderer.TrackTypeData> trackTypes = TrackRenderer.getTrackTypes();
                boolean isTitleOnly = false;
                if (trackTypes != null) {
                    TrackRenderer.TrackTypeData trackData = trackTypes.get(items.get(i));
                    if (trackData != null) {
                        boolean hasIcon = trackData.icon() != null;
                        boolean hasDesc = trackData.description() != null && !trackData.description().isEmpty();
                        isTitleOnly = !hasIcon && !hasDesc;
                    }
                }

                int buttonHeight = isTitleOnly ? 25 : BUTTON_HEIGHT;
                int buttonY = currentY - scrollOffset;
                if (mouseX >= listX && mouseX <= listX + lastListWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                    hoveredButtonIndex = i;
                    break;
                }
                
                currentY += buttonHeight + BUTTON_SPACING;
            }
        }
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

        List<String> items = filteredTrainIds != null ? filteredTrainIds : availableTrainIds;
        int contentY = getGuiY() + CONTENT_PADDING;

        int firstButtonY = contentY + 24 + BUTTON_SPACING + 24 + BUTTON_SPACING;
        int buttonIndex = (y - firstButtonY + scrollOffset) / (BUTTON_HEIGHT + BUTTON_SPACING);
        
        if (buttonIndex >= 0 && buttonIndex < items.size()) {
            String carriageId = items.get(buttonIndex);
            boolean canAdd = canAddCarriage(carriageId);
            
            if (!canAdd) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BUTTON_DISABLED, addButtonX, y, addButtonWidth, addButtonHeight);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+"), addButtonX + addButtonWidth/2, y + addButtonHeight/2 - 4, 0xFF888888);
                return;
            }
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, isHovered ? BUTTON_HIGHLIGHTED : BUTTON_NORMAL, addButtonX, y, addButtonWidth, addButtonHeight);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("+"), addButtonX + addButtonWidth/2, y + addButtonHeight/2 - 4, 0xFFFFFFFF);
    }

    private boolean isAddButtonHovered(int addX, int addY, int width, int height, int mouseX, int mouseY) {
        return mouseX >= addX && mouseX <= addX + width && mouseY >= addY && mouseY <= addY + height;
    }

    private boolean canAddCarriage(String carriageId) {
        if (segment == null || !"siding".equals(selectedType)) {
            return true;
        }

        double sidingLength = calculateSidingLength();
        double currentTotalLength = calculateCurrentCarriageLength();
        double newCarriageLength = getCarriageLength(carriageId);

        return (currentTotalLength + newCarriageLength) <= sidingLength;
    }

    private double calculateSidingLength() {
        if (segment == null) return 0.0;

        double dx = segment.end().getX() - segment.start().getX();
        double dy = segment.end().getY() - segment.start().getY();
        double dz = segment.end().getZ() - segment.start().getZ();
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double calculateCurrentCarriageLength() {
        if (currentCarriages == null || currentCarriages.isEmpty()) {
            return 0.0;
        }
        
        double totalLength = 0.0;
        final double SPACING_BUFFER = 1.0;
        
        for (String carriageId : currentCarriages) {
            double carriageLength = getCarriageLength(carriageId);
            totalLength += carriageLength + SPACING_BUFFER;
        }

        if (totalLength > 0) {
            totalLength -= SPACING_BUFFER;
        }
        
        return totalLength;
    }

    private double getCarriageLength(String carriageId) {
        var trainData = TrainConfigLoader.getTrainType(carriageId);
        if (trainData != null) {
            return trainData.length();
        }
        
        return 25.0;
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

        int maxSpeed;
        try {
            maxSpeed = Integer.parseInt(maxSpeedField.getText());
        } catch (NumberFormatException e) {
            TrainModClient.LOGGER.warn("Invalid max speed input, using default: {}", maxSpeedField.getText(), e);
            maxSpeed = segment.getMaxSpeedKmh();
        }

        String stationName = stationNameField.getText();

        UpdateTrackSegmentPayload trackPayload = new UpdateTrackSegmentPayload(
            segment.start(),
            segment.end(),
            selectedTrackId,
            selectedType,
            dwellTimeSeconds,
            curvature,
            null,
            (isRouteTab && selectedRouteId != null && !selectedRouteId.isEmpty()) ? selectedRouteId : segment.getRouteId(),
            maxSpeed,
            stationName,
            segment.getStationId()
        );

        TrainModClientNetworking.sendToServer(trackPayload);
        
        String newRouteId = (isRouteTab && selectedRouteId != null && !selectedRouteId.isEmpty()) ? selectedRouteId : segment.getRouteId();
        
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

            String trainId = segment.getTrainId();
            if (trainId == null || trainId.trim().isEmpty()) {
                trainId = "temp_" + System.currentTimeMillis();
            }
            
            UpdateTrainConfigPayload trainPayload = new UpdateTrainConfigPayload(trainId, currentCarriages, carriageLengths, bogieInsets, trackSegmentKey, boundingBoxWidths, boundingBoxLengths, boundingBoxHeights);
            TrainModClientNetworking.sendToServer(trainPayload);
        }
        
        if (newRouteId != null && !newRouteId.trim().isEmpty()) {
            generateAndSendPath(newRouteId);
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

    private void generateAndSendPath(String routeId) {
        if (routeId == null || routeId.trim().isEmpty() || segment == null) {
            return;
        }
        
        String trainId = segment.getTrainId();
        if (trainId == null || trainId.trim().isEmpty()) {
            trainId = "temp_" + System.currentTimeMillis();
        }
        
        if (segment.start() == null || segment.end() == null) {
            return;
        }
        
        GeneratePathPayload payload = new GeneratePathPayload(trainId, routeId, segment.start(), segment.end());
        TrainModClientNetworking.sendToServer(payload);
    }
}
