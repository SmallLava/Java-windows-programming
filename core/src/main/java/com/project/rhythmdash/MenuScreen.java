package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class MenuScreen implements Screen {
    private final Stage stage; // UI 的舞台

    public MenuScreen(final MainGame game) {

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label titleLabel = new Label("RHYTHM DASH", game.skin, "title");

        TextButton startButton = new TextButton("START GAME", game.skin);
        startButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                GameScreen gameScreen = new GameScreen(game);
                game.setScreen(gameScreen);
                gameScreen.activateInputProcessor();
            }
        });

        final Slider volumeSlider = new Slider(0, 1, 0.1f, false, game.skin);
        volumeSlider.setValue(game.globalVolume);
        Label volumeLabel = new Label("Volume: " + (int)(game.globalVolume * 100) + "%", game.skin);

        volumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.globalVolume = volumeSlider.getValue();
                volumeLabel.setText("Volume: " + (int)(game.globalVolume * 100) + "%");
            }
        });

        table.add(titleLabel).padBottom(50).row();
        table.add(startButton).width(200).height(60).padBottom(20).row();
        table.add(new Label("Settings", game.skin)).padBottom(10).row();
        table.add(volumeLabel).padBottom(5).row();
        table.add(volumeSlider).width(300).row();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.2f, 1);
        
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
    
    @Override public void show() {}
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
}