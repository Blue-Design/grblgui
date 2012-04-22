package cody.grblgui;

import java.io.IOException;

import cody.gcode.GCodeFile;
import cody.gcode.GCodeParser;
import cody.grbl.GrblStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class MainScreen implements Screen, InputProcessor{

	GrblStream grbl;
	
	Workspace workspace;
	PerspectiveCamera camera;
	OrthographicCamera orthocam;
	Tool tool;
	Tool current;
	Toolpath toolpath;
	
	SpriteBatch spriteBatch;
	BitmapFont font;
	GCodeFile file;
	String filename;
	String device;

	float clicktimer;
	
	float postimer;
	Vector3 lastpos = new Vector3(0,0,0);
	
	float speed;
	
	public MainScreen(String _filename, String _device) {
		filename = _filename;
		device = _device;
	}
	
	@Override
	public void dispose() {
		grbl.dispose();
	}

	@Override
	public void hide() {
		grbl.dispose();
	}

	@Override
	public void pause() {
	}

	@Override
	public void render(float arg0) {
		Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Gdx.gl20.glEnable(GL20.GL_BLEND);
		Gdx.gl20.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
		//Gdx.gl20.glDisable(GL20.GL_BLEND);
		//Gdx.gl20.glLineWidth(2);
		
		float t = arg0;
		clicktimer+=t;
		postimer+=t;

		Vector3 tooltargetpos = grbl.toolPosition.cpy();
		Vector3 d = tooltargetpos.sub(current.position);
		float l = d.len();
		Vector3 result = current.position.add(d.mul(Math.min(t * 10f, 1f)));
		current.position = result;
		//camera.rotate(1, 0, 1, 1);
		camera.lookAt(current.position.x, current.position.y, current.position.z);
		camera.update(true);
		

		if(postimer >= 1f) {
			speed = current.position.dst(lastpos) / postimer * 60f;
			lastpos = current.position.cpy();
			postimer = 0;
		}

		int currentline = toolpath.currentLine = grbl.streamer != null ? grbl.streamer.currentLine : -1;
		
		Matrix4 matrix = camera.combined.cpy();
		workspace.draw(matrix);
		toolpath.draw(matrix);
		tool.draw(matrix);
		current.draw(matrix);

		orthocam.update();
		spriteBatch.setProjectionMatrix(orthocam.projection);
		spriteBatch.setTransformMatrix(orthocam.view);
		spriteBatch.begin();
		int maxlines = Gdx.graphics.getHeight() / 20 - 1;
		for(int i = currentline;i > 0 && i > currentline - maxlines;--i) {
			font.draw(spriteBatch, file.gcode.get(i).getContent(), 20, 20 + (currentline - i) * 20);
		}
		font.draw(spriteBatch, "help: press 's' to start/stop streaming of '" + filename + "', 'p' for feed hold, 'q' to quit", 200, Gdx.graphics.getHeight() - 20);
		font.draw(spriteBatch, "position: X" + grbl.toolPosition.x + "Y" + grbl.toolPosition.y + "Z" +grbl.toolPosition.z, Gdx.graphics.getWidth() - 220, 100);
		font.draw(spriteBatch, "status: " + (grbl.isStreaming() ? "streaming " : "") + (grbl.isHold() ? "hold" : "running"), Gdx.graphics.getWidth() - 220, 80);
		font.draw(spriteBatch, "speed: " + Float.toString(speed)+"mm/min", Gdx.graphics.getWidth() - 220, 40);
		if(grbl.isStreaming()) {
			font.draw(spriteBatch, "eta:" + Float.toString(toolpath.getEta())+"min", Gdx.graphics.getWidth() - 220, 60);
			font.draw(spriteBatch, "duration: " + Float.toString(toolpath.duration)+"min", Gdx.graphics.getWidth() - 220, 20);
		}
		spriteBatch.end();
		
		if(clicktimer >= 0.5f)
		{
			clicktimer = 0;
		for(int i=0;i<5;++i) {
    		if(Gdx.input.isTouched(i)) {
    			int x = Gdx.input.getX(i);
    			int y = Gdx.input.getY(i);
    			Vector3 pos = workspace.intersect(camera.getPickRay(x, y));
    			tool.position = pos;
    				if(!grbl.isStreaming()) {
    					String cmd = "G0X" + pos.x + "Y" + pos.y + "Z" + pos.z + "\n";
    					grbl.send(cmd.getBytes());
    					System.out.print(cmd);
    				}
    		}
		}
		
		}
	}

	@Override
	public void resize(int width, int height) {
		Gdx.graphics.getGL20().glViewport(0, 0, width, height);
		camera.viewportHeight = height;
		camera.viewportWidth = width;
		orthocam.viewportHeight = height;
		orthocam.viewportWidth = width;
		orthocam.position.x = width/2;
		orthocam.position.y = height/2;
	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void show() {

		spriteBatch = new SpriteBatch();
		font = new BitmapFont();
		font.setColor(0, 1, 0, 1);
		
		workspace = new Workspace(-150,150,-200,200,0,50);
		//camera = new OrthographicCamera(800,450);
		camera = new PerspectiveCamera();
		camera.translate(150, 150, 150);
		camera.up.x = 0;
		camera.up.y = 0;
		camera.up.z = 1;
		camera.lookAt(0, 0, 0);
		camera.fieldOfView = 30;
		camera.far = 10000;
		camera.near = 10;
		camera.viewportHeight = Gdx.graphics.getHeight();
		camera.viewportWidth = Gdx.graphics.getWidth();
		
		orthocam = new OrthographicCamera(camera.viewportWidth,camera.viewportHeight);
		
		tool = new Tool();
		current = new Tool();
		
		

		try {
			file = GCodeParser.parseFile(filename);

			grbl = new GrblStream(device);
			
			toolpath = Toolpath.fromGCode(file);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		Gdx.input.setInputProcessor(this);
	}
	
	@Override
	public boolean keyTyped(char arg0) {
		if(arg0 == 'p') {
			grbl.pause();
		}
		else if(arg0 == 's') {
			if(grbl.isStreaming())
				grbl.stopStream();
			else
				grbl.stream(file);
		}
		else if(arg0 == 'q') {
			Gdx.app.exit();
		}
		return false;
	}

	@Override
	public boolean keyDown(int arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean keyUp(int arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean scrolled(int arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchDown(int arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchDragged(int arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchMoved(int arg0, int arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchUp(int arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		return false;
	}
}
