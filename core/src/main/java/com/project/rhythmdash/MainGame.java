package com.project.rhythmdash;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.project.rhythmdash.tools.SkinTools;

public class MainGame extends Game {
    
    public SpriteBatch batch;
    public Skin skin;

    public float globalVolume = 0.5f;
    
    @Override
    public void create() {
        batch = new SpriteBatch();
        this.skin = SkinTools.createBasicSkin();
        
        this.setScreen(new MenuScreen(this));
    }

    @Override
    public void render() {
        super.render();
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}