package game;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.KeyStroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;

public class Game {

    // main class instances
    public static Game game;
    public static Frame gameFrame;
    public static Panel gamePanel;
    
    //player
    public Player player;
    
    // basic background color
    public Color bgColor = new Color (2, 23, 33); // dark blue
    
    // desired frames per sec
    public final double desiredFPS = 60;
    // game logic loop's nanoseconds per frame
    public final double nsPerTick = 1000000000D / desiredFPS;
    // game drawing loop's nanoseconds per frame
    public final double nsPerDraw = nsPerTick;
    // fixed timestep in seconds, used by fixedLoop
    public final float secPerTick = (float) (nsPerTick / 1000000000D);
    // max delay above which we skip a fixedLoop update
    public final double nsMaxDelayTolerated = 1000000000D / 2;
    // timescale
    public float timescale = 1f;
    // game time in seconds (resets on stageLoad)
    public double time;

    // initialization check
    public boolean hasInit = false;
    // loop killswitch
    public boolean running = true;

    // window size
    public final int windowWidth = 1024;
    public final int windowHeight = 768;
    // cell size
    final int cell = 16;
    // view scale
    final float scale = 3f;
 
    // controls
    public boolean pressLeft;
    public boolean pressRight;
    public boolean pressUp;
    public boolean pressDown;
    public boolean paused;
    
    // draw transform
    AffineTransform at = new AffineTransform ();
    
    // images
    BufferedImage imgEnemies;
    //BufferedImage imgExplosion;
    BufferedImage imgItems;
    BufferedImage imgMeter;
    BufferedImage imgPlayer;
    BufferedImage imgTiles;
    
    // stage
    String[] stageFiles = { "stage.bmp", "stage2.bmp", "stage3.bmp" };
    int curStage = 0;
    int stageWidth = 0;
    int stageHeight = 0;
    final int maxStageWidth = 1024;
    final int maxStageHeight = 1024;
    byte[][] stageCells;
    final byte parseTypes = 20; // number of tile types to read on stage.bmp
    final byte tile1Base = 100; // base number of type 1 tiles
    final byte tile2Base = 20;  // base number of type 2 tiles
    final byte tileLava = 8;  // number of lava tile
    final byte tileLavaTop = 19; // number of lava top tile
    final byte tileEnd = 9;  // number of ending tile
    
    // stage's random seed (for interchangeable tiles)
    public int rndSeed = 1;
    
    // chance of decorative tile on type 0 tile
    float decorChance = .02f;
    float grassChance = .25f;
    
    // lava wave frequency
    float lavaFrq = .8f;
    
    // position
    float startPosX = 128f;
    float startPosY = 128f;
    
    // camera
    float xCamera;
    float yCamera;
    float lerpCamera = 1f; // factor of camera smooth motion (1 = sharp, < 1 = smooth)
    
    // ending portal anim
    AnimData endAnim = new AnimData (0f, 3f, 1f, 3);
    
    // fonts
    Font fontLarge;
    // shadow distance in DrawText()
    float textShadowDist = 3f;
    // typewriter effect on ending text
    float textTypewriter = 0f;
    float textTypewriterSpeed = .7f;
   
    public static void main (String[] args) {

	game = new Game ();
	gameFrame = game.new Frame ();
	gameFrame.start ();
    }

    // game initialization
    void Initialize () {
	
	if (hasInit) return;
	
	// load fonts
	boolean fontLoaded = true;
	try {
	    fontLarge = Font.createFont(Font.TRUETYPE_FONT, new File("8bitOperatorPlus-Bold.ttf"));
	} catch (FontFormatException | IOException ex) {
	    fontLoaded = false;
	}
	if (!fontLoaded) fontLarge = new Font("Monospaced", Font.PLAIN, 1);
	
	// load images
	try {
	    imgEnemies = ImageIO.read (new File ("img/enemies.png"));
	    //imgExplosion = ImageIO.read (new File ("img/explosion.png"));
	    imgItems = ImageIO.read (new File ("img/items.png"));
	    imgMeter = ImageIO.read (new File ("img/meter.png"));
	    imgPlayer = ImageIO.read (new File ("img/player.png"));
	    imgTiles = ImageIO.read (new File ("img/tiles.png"));
	} catch (IOException e) {
	    System.out.println ("Failed to load 1 or more images.");
	}
	
	// initialize transform
	at.setToIdentity ();
	at.scale (scale, scale); //scale with our viewport size
	
	// load stage
	loadStage (stageFiles[curStage]);
	
	// create player
	player = new Player (startPosX, startPosY);
	xCamera = player.x;
	yCamera = player.y;
	
 	hasInit = true;
    }
    
    // loads a stage bitmap image and parse the pixel values into stage form
    void loadStage (String fname) {
	
	// load stage image
	BufferedImage img;
	try {
	    img = ImageIO.read (new File (fname));
	} catch (IOException ex) {
	    System.out.println ("Failed to read stage " + fname);
	    return;
	}
	// abort if image exceeds dimension limits
	int w = img.getWidth ();
	int h = img.getHeight ();
	if (w > maxStageWidth || h > maxStageHeight + 1) {
	    System.out.println ("Stage dimensions too large: " + w + "x" + h);
	    return;
	}
	stageWidth = w;
	stageHeight = h - 1;
	System.out.println ("Stage dimensions: " + w + "x" + (h - 1));
	
	// check for correctness of tile bases
	if (tile1Base < 10 || tile2Base < 10 || tile2Base + 8 >= tile1Base) {
	    System.out.println ("Invalid tile bases specified in code.");
	    return;
	}
	
	//reset time
	time = 0;
	
	// read first row (top) pixels as tile type definitions
	int[] tileType = new int[parseTypes];
	stageCells = new byte[w][h - 1];
	for (int i = 0; i < Math.min (w, parseTypes); i++) {
	    // read pixel & store as type
	    var pixel = img.getRGB (i, 0);
	    tileType[i] = pixel;
	    //System.out.println ("Type " + i + " = " + pixel);
	}
	
	// parse stage (turn pixel colors to tiles)
	for (int j = 1; j < h; j++) {
	    for (int i = 0; i < w; i++) {
		// read pixel
		var pixel = img.getRGB (i, j);
		stageCells[i][j - 1] = 0;
		// read as tile
		for (byte k = 0; k < parseTypes; k++) {
		    if (pixel == tileType[k]) {
			if (k == 1) k = tile1Base; // solid tiles, type 1
			if (k == 2) k = tile2Base;  // solid tiles, type 2
			stageCells[i][j - 1] = k;
			break;
		    }
		}
		//System.out.print (stageCells[i][j - 1]);
	    }
	    //System.out.println ();
	}
	
	// perform autotile and apply entities
	for (int j = 0; j < stageHeight; j++) {
	    for (int i = 0; i < stageWidth; i++) {
		// autotile for type 1
		if (stageCells[i][j] >= tile1Base) {
		    // check edges
		    boolean isTop = (j == 0 || stageCells[i][j - 1] < tile1Base);
		    boolean isBottom = !isTop && (j == stageHeight - 1 || stageCells[i][j + 1] < tile1Base);
		    boolean isLeft = (i == 0 || stageCells[i - 1][j] < tile1Base);
		    boolean isRight = !isLeft && (i == stageWidth - 1 || stageCells[i + 1][j] < tile1Base);
		    // autotile (turn to tile according to nearby edges)
		    stageCells[i][j] = (byte)(tile1Base + (isTop ? 1 : (isBottom ? 2 : 0)) + (isLeft ? 3 : (isRight ? 6 : 0)));
		}
		// autotile for type 2
		else if (stageCells[i][j] == tile2Base) {
		    // check if horizontal 2-block
		    if (!cellIsType2 (i, j - 1) && !cellIsType2 (i, j + 1)) {
			// left-side
			if (!cellIsType2 (i - 1, j) && !cellIsType2 (i + 2, j)
				&& cellIsType2 (i + 1, j, true)) {
			    stageCells[i][j] = tile2Base + 1;
			    stageCells[i + 1][j] = tile2Base + 2;
			}
			// right-side
			else if (!cellIsType2 (i - 2, j) && !cellIsType2 (i + 1, j)
				&& cellIsType2 (i - 1, j, true)) {
			    stageCells[i - 1][j] = tile2Base + 1;
			    stageCells[i][j] = tile2Base + 2;
			}
		    }
		    // check if vertical 2-block
		    else if (!cellIsType2 (i - 1, j) && !cellIsType2 (i + 1, j)) {
			// top-side
			if (!cellIsType2 (i, j - 1) && !cellIsType2 (i, j + 2)
				&& cellIsType2 (i, j + 1, true)) {
			    stageCells[i][j] = tile2Base + 3;
			    stageCells[i][j + 1] = tile2Base + 4;
			}
			// bottom-side
			else if (!cellIsType2 (i, j - 2) && !cellIsType2 (i, j + 1)
				&& cellIsType2 (i, j - 1, true)) {
			    stageCells[i][j - 1] = tile2Base + 3;
			    stageCells[i][j] = tile2Base + 4;
			}
		    }
		    // check if square 4-block
		    else if (cellIsType2 (i + 1, j, true) && cellIsType2 (i, j + 1, true)
			    && cellIsType2 (i + 1, j + 1, true)) {
			stageCells[i][j] = tile2Base + 5;
			stageCells[i + 1][j] = tile2Base + 6;
			stageCells[i][j + 1] = tile2Base + 7;
			stageCells[i + 1][j + 1] = tile2Base + 8;
		    }
		}
		// player starting position
		else if (stageCells[i][j] == 3) {
		    stageCells[i][j] = 0;
		    startPosX = cell * (i + .5f);
		    startPosY = cell * (j + .5f) - 1; // -1 to prevent fall-through
		}
		// coin
		else if (stageCells[i][j] == 4) {
		    stageCells[i][j] = 0;
		    new Coin (cell * (i + .5f), cell * (j + .5f));
		}
		// slime enemy
		else if (stageCells[i][j] == 5) {
		    stageCells[i][j] = 0;
		    new Slime (cell * (i + .5f), cell * (j + .5f) - 1);
		}
		// slime enemy
		else if (stageCells[i][j] == 6) {
		    stageCells[i][j] = 0;
		    new Bat (cell * (i + .5f), cell * (j + .5f) - 1);
		}
		// slime enemy
		else if (stageCells[i][j] == 7) {
		    stageCells[i][j] = 0;
		    new Imp (cell * (i + .5f), cell * (j + .5f) - 1);
		}
		// lava top
		else if (stageCells[i][j] == tileLava) {
		    if (j < 1 || (stageCells[i][j - 1] != tileLava
			    && stageCells[i][j - 1] != tileLavaTop))
			stageCells[i][j] = tileLavaTop;
		}
		//System.out.print ((char)(Character.valueOf ('A') + stageCells[i][j]));
	    }
	    //System.out.println ();
	}
    }
    
    // functions to check if a cell is type 2 (non-precise includes autotile versions)
    boolean cellIsType2 (int x, int y) {
	
	return cellIsType2 (x, y, false);
    }
    boolean cellIsType2 (int x, int y, boolean precise) {
	
	return x >= 0 && y >= 0 && x < stageWidth && y < stageHeight &&
		(precise ? stageCells[x][y] == tile2Base :
		(stageCells[x][y] >= tile2Base && stageCells[x][y] <= tile2Base + 8));
    }
    // function to check if a cell is solid
    boolean cellIsSolid (int x, int y) {
	
	return x >= 0 && y >= 0 && x < stageWidth && y < stageHeight && stageCells[x][y] >= tile2Base;
    }
    
    // fixed-timestep loop update (dt is in seconds)
    void Loop (float dt) {

	if (!hasInit) return;
	
	// quit on escape
	if (paused) {
	    running = false;
	    System.exit (0);
	}
	
	// enemies loop
	for (int i = 0; i < enemies.size (); i++) enemies.get (i).Loop (dt);
	
	// player loop
	player.Loop (dt);
	
	// coins loop
	coinAnim.AnimatePingPong (dt);
	for (int i = 0; i < activeCoins.size (); i++) activeCoins.get (i).Loop (dt);
	
	// ending portal animation
	endAnim.AnimatePingPong (dt);
	
	// smooth camera follow
	if (!player.dead) {
	    xCamera += lerpCamera * (player.x - xCamera);
	    yCamera += lerpCamera * (player.y - yCamera);
	    var d = gamePanel.getSize ();
	    xCamera = Math.max (d.width / 2f / scale, Math.min (cell * stageWidth - d.width / 2f / scale, xCamera));
	    yCamera = Math.max (d.height / 2f / scale, Math.min (cell * stageHeight - d.height / 2f / scale, yCamera));
	}
	
	// ending text typewriter effect
	if (player.ended) {
	    textTypewriter = Math.min (1f, textTypewriter + textTypewriterSpeed * dt);
	}
	else textTypewriter = 0f;
    }

    // redrawing function
    void Draw (Graphics g, Dimension d) {

	if (!hasInit) return;
	
	Graphics2D g2 = (Graphics2D) g;
	int w = d.width;
	int h = d.height;

	// enable antialiasing etc
	//g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	//g2.setRenderingHint (RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
	//g2.setRenderingHint (RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
	//g2.setRenderingHint (RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	//g2.setRenderingHint (RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
	//g2.setRenderingHint (RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

	// base transform
	var backupAT = g2.getTransform ();
	g2.transform (at);
	var midAt = g2.getTransform ();
	g2.translate ((double)(-xCamera + w / 2f / scale), (double)(-yCamera + h / 2f / scale));
	var finalAt = g2.getTransform ();
	
	// draw background stage
	DrawStage (g2, false);
	// draw enemies
	for (int i = 0; i < enemies.size (); i++) enemies.get (i).Draw (g2);
	// draw player
	player.Draw (g2);
	// draw foreground stage
	DrawStage (g2, true);
	// draw coins
	for (int i = 0; i < activeCoins.size (); i++) activeCoins.get (i).Draw (g2);
	// draw health meter
	g2.setTransform (midAt);
	DrawHealth (g2, d);
	g2.setTransform (finalAt);
	// draw ending text
	if (player.ended) {
	    var s = "Stage Complete";
	    if (textTypewriter < .3f) s = s.substring (0, (int)(s.length () * textTypewriter / .3f));
	    DrawText (g2, fontLarge, s, 0, -24, 16f);
	    if (textTypewriter > .5f) {
		s = "Coins found: " + player.coinsCollected + " / " + allCoins.size ();
		if (textTypewriter < .75f) s = s.substring (0, (int)(s.length () * (textTypewriter - .5f) / .25f));
		DrawText (g2, fontLarge, s, 0, 70, 8f);
		if (textTypewriter > .75f) {
		    s = curStage >= stageFiles.length - 1 ? "All stages complete - Thanks for playing!" : "Press Jump for next stage";
		    if (textTypewriter < 1f) s = s.substring (0, (int)(s.length () * (textTypewriter - .75f) / .25f));
		    DrawText (g2, fontLarge, s, 0, 85, 8f);
		}
	    }
	}
	
	// restore drawing transform
	g2.setTransform (backupAT);
    }
    
    void DrawStage (Graphics2D g, boolean foregroundLayer) {
	
	// start seed-based random generation
	var rnd = new Random ();
	rnd.setSeed (rndSeed);
	
	// get required data
	//int cellsDrawn = 0;
	var d = gamePanel.getSize ();
	var w2 = d.width / 2f / scale;
	var h2 = d.height / 2f / scale;
	int lavaWaves = (time * lavaFrq) % 1f > .5f ? 1 : 0;
	// draw stage
	for (int i = 0; i < stageWidth; i++) {
	    for (int j = 0; j < stageHeight; j++) {
		// read value from random pool
		float modifier = rnd.nextFloat ();
		// skip tile if not within view
		if (i * cell + cell < xCamera - w2 || j * cell + cell < yCamera - h2
			|| i * cell > xCamera + w2 || j * cell > yCamera + h2) continue;
		// get correct tile to show
		int xx = -1, yy = -1;
		if (foregroundLayer) {
		    // draw foreground tiles
		    switch (stageCells[i][j]) {
			case tileLava: // lava (8)
			    xx = 4;
			    yy = 1;
			    break;
			case tileLavaTop: // lava top (19)
			    xx = 4 + (i % 2 == 0 ? lavaWaves : 1 - lavaWaves);
			    yy = 0;
			    break;
			default:
		    }
		}
		else {
		    // draw background tiles
		    switch (stageCells[i][j]) {
			case 0: // maybe decorative tile
			    if (j < stageHeight - 1 && stageCells[i][j + 1] >= tile1Base) {
				if (modifier < grassChance) { // grass
				    xx = (int)Math.round (modifier * 3);
				    yy = 2;
				}
			    }
			    else if (modifier < decorChance) { // wall rocks
				xx = 2 + (int)Math.round (modifier / decorChance);
				yy = 1;
			    }
			    break;
			case tile1Base: // type 1 center
			    xx = 1 + (int)Math.round (.25f + .75f * modifier);
			    yy = 4;
			    break;
			case tile1Base + 1: // type 1 center top
			    xx = 1 + (int)Math.round (modifier);
			    yy = 3;
			    break;
			case tile1Base + 2: // type 1 center bottom
			    xx = 1 + (int)Math.round (modifier);
			    yy = 0;
			    break;
			case tile1Base + 3: // type 1 left
			    xx = 0;
			    yy = 4;
			    break;
			case tile1Base + 4: // type 1 left top
			    xx = 0;
			    yy = 3;
			    break;
			case tile1Base + 5: // type 1 left bottom
			    xx = 0;
			    yy = 0;
			    break;
			case tile1Base + 6: // type 1 right
			    xx = 3;
			    yy = 4;
			    break;
			case tile1Base + 7: // type 1 right top
			    xx = 3;
			    yy = 3;
			    break;
			case tile1Base + 8: // type 1 right bottom
			    xx = 3;
			    yy = 0;
			    break;
			case tile2Base: // type 2 single
			    xx = 5;
			    yy = 2;
			    break;
			case tile2Base + 1: // type 2 hor-block left
			    xx = 6;
			    yy = 0;
			    break;
			case tile2Base + 2: // type 2 hor-block right
			    xx = 7;
			    yy = 0;
			    break;
			case tile2Base + 3: // type 2 ver-block top
			    xx = 7;
			    yy = 3;
			    break;
			case tile2Base + 4: // type 2 ver-block bottom
			    xx = 7;
			    yy = 4;
			    break;
			case tile2Base + 5: // type 2 4-block top-left
			    xx = 6;
			    yy = 1;
			    break;
			case tile2Base + 6: // type 2 4-block top-right
			    xx = 7;
			    yy = 1;
			    break;
			case tile2Base + 7: // type 2 4-block bottom-left
			    xx = 6;
			    yy = 2;
			    break;
			case tile2Base + 8: // type 2 4-block bottom-right
			    xx = 7;
			    yy = 2;
			    break;
			case tileLava: // lava (8)
			    xx = 4;
			    yy = 1;
			    break;
			case tileLavaTop: // lava top (19)
			    xx = 4 + (i % 2 == 0 ? lavaWaves : 1 - lavaWaves);
			    yy = 0;
			    break;
			case tileEnd:
			    if (!player.ended) {
				xx = (int)(endAnim.timer * endAnim.frames);
				yy = 0;
			    }
			    break;
			default:
		    }
		}
		// draw tile
		if (xx != -1) {
		    g.drawImage (stageCells[i][j] == tileEnd ? imgItems : imgTiles,
			cell * i, cell * j, cell * (i + 1), cell * (j + 1),
			cell * xx, cell * yy, cell * (xx + 1), cell * (yy + 1), null);
		    //cellsDrawn++;
		}
	    }
	}
	//System.out.println (cellsDrawn);
    }
    
    void DrawText (Graphics2D g, Font font, String text, float dx, float dy, float size) {
	
	// set font and parameters
	var metrics = g.getFontMetrics(font);
	float h = metrics.getHeight() * .5f;
	float w = metrics.stringWidth(text) * .5f;
	g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
	// scale by size
	var tt = g.getTransform ();
	var scaleT = new AffineTransform ();
	scaleT.scale (size, size);
	g.translate (xCamera + dx - w * size, yCamera + dy - h * size);
	g.transform (scaleT);
	// draw text
	var ss = textShadowDist / (scale * size);
	g.setColor (Color.DARK_GRAY);
        g.drawString(text, ss, ss); // shadow
	g.setColor (Color.WHITE);
        g.drawString(text, 0, 0); // main
	// restore transform
	g.setTransform (tt);
    }
    
    void DrawHealth (Graphics2D g, Dimension d) {
	
	if (player.health <= 0) return;
	var w = d.getWidth ();
	var h = d.getHeight ();
	var bak = g.getTransform ();
	var xx = 16f / scale; //xCamera + (-w / 2f + 16) / scale;
	var yy = 16f / scale; //yCamera + (-h / 2f + 16) / scale;
	g.translate (xx, yy);
	var ph = Math.min (player.health, player.maxHealth);
	var mw = imgMeter.getWidth ();
	var mh = imgMeter.getHeight ();
	var myscale = .75f;
	g.drawImage (imgMeter, 0, 0, (int)(mw * scale * myscale), (int)(mh * myscale),
		0, 7 * (ph - 1), mw, 7 * ph, null);
	g.setTransform (bak);
    }
    
    // Character class
    public class Character {
	
	// position
	float x;
	float y;
	// bounding box
	float bboxX1 = -cell * .25f;
	float bboxX2 =  cell * .25f;
	float bboxY1 = -cell * .25f;
	float bboxY2 =  cell * .5f - 1;
	// scale (only for drawing, does not affect bbox)
	float xScale = 1f;
	float yScale = 1f;
	// motion
	float xSpeed = 0;
	float ySpeed = 0;
	float gravity = 300;
	final float maxSpeed = 150;
	boolean grounded = true;
	boolean touchedWall = false;
    
	public void Loop (float dt) {
	    
	    // check if grounded
	    grounded = ySpeed >= 0 && checkForGround ();
	    // gravity etc
	    if (!grounded) ySpeed += gravity * dt;
	    ySpeed = Math.min (ySpeed, maxSpeed);
	    // displace
	    Move (xSpeed * dt, ySpeed * dt);
	}
	
	void Move (float dx, float dy) {
	    
	    // x axis
	    if (dx != 0) {
		boolean free = true;
		// current x of player edge
		float xCurrent = (dx > 0 ? x + bboxX2 : x + bboxX1);
		// x of player edge after move
		float xx = xCurrent + dx;
		// check for possible obstacles is needed only if the edge traverses different cells
		if ((int)(xCurrent / (float)cell) != (int)(xx / (float)cell)) {
		    // since entities are smaller than blocks, only check top & bottom y's
		    if (cellIsSolid ((int)(xx / cell), (int)((y + bboxY1) / cell))) free = false;
		    else if (cellIsSolid ((int)(xx / cell), (int)((y + bboxY2) / cell))) free = false;
		}
		// apply motion
		if (free) x += dx;
		else {
		    x += (dx > 0 ? Math.ceil (x / cell) * cell - 1 : Math.floor (x / cell) * cell + 1) - xCurrent;
		    xSpeed = 0;
		    touchedWall = true;
		}
	    }
	    // y axis
	    if (dy != 0) {
		boolean free = true;
		// current y of player edge
		float yCurrent = (dy > 0 ? y + bboxY2 : y + bboxY1);
		// y of player edge after move
		float yy = yCurrent + dy;
		// check for possible obstacles is needed only if the edge traverses different cells
		if ((int)(yCurrent / (float)cell) != (int)(yy / (float)cell)) {
		    // since entities are smaller than blocks, only check left & right x's
		    if (cellIsSolid ((int)((x + bboxX1) / cell), (int)(yy / cell))) free = false;
		    else if (cellIsSolid ((int)((x + bboxX2) / cell), (int)(yy / cell))) free = false;
		}
		// apply motion
		if (free) y += dy;
		else {
		    y += (dy > 0 ? Math.ceil (y / cell) * cell - 1 : Math.floor (y / cell) * cell + 1) - yCurrent;
		    ySpeed = 0;
		}
	    }
	}
	
	boolean checkForGround () {

	    float dy = 1f;
	    // current y of player edge
	    float yCurrent = y + bboxY2;
	    // y of player edge after move
	    float yy = yCurrent + dy;
	    // check for possible obstacles is needed only if the edge traverses different cells
	    if ((int) (yCurrent / (float) cell) != (int) (yy / (float) cell)) {
		// since entities are smaller than blocks, only check left & right x's
		if (cellIsSolid ((int)((x + bboxX1) / cell), (int)(yy / cell))) return true;
		else if (cellIsSolid ((int)((x + bboxX2) / cell), (int)(yy / cell))) return true;
	    }
	    return false;
	}
	
	public void Draw (Graphics2D g) { }
    }

    // player class
    public class Player extends Character {
	
	// motion
	float prevWalk = 0;
	final float walkSpeed = 64;
	final float jumpSpeed = -150;
	final float hurtJumpSpeed = -120;
	// animation
	AnimData anim = new AnimData (.5f, 1.75f, 1f, 3);
	// damage flash
	float flashTimer;
	float flashDur = .2f;
	// health
	int maxHealth = 3;
	int health = maxHealth;
	// temp invincibility after damage
	public float invincible;
	float invincibleDur = 2f;
	float invincibleFlashFrq = 3f;
	boolean justHurt = false; // used for animation
	// dead
	boolean dead = false;
	float deadTimer;
	final float deadDur = 2.2f;
	// coin count
	public int coinsCollected = 0;
	// cells which we touch
	int touchCellMinX, touchCellMinY, touchCellMaxX, touchCellMaxY;
	// lava sink speed
	float lavaSinkSpeed = 2f;
	boolean burntByLava = false;
	// whether we reached stage end
	boolean ended = false;
	
	public Player (float x, float y) {
	    this.x = x;
	    this.y = y;
	}
	
	public void Reset () {
	    
	    x = startPosX;
	    y = startPosY;
	    xSpeed = 0;
	    ySpeed = 0;
	    prevWalk = 0;
	    anim.timer = .5f;
	    anim.direction = 1f;
	    flashTimer = 0f;
	    dead = false;
	    deadTimer = 0f;
	    burntByLava = false;
	    ended = false;
	    health = maxHealth;
	    justHurt = false;
	}
	
	@Override
	public void Loop (float dt) {
	    
	    // flash timer
	    if (flashTimer > 0) flashTimer -= dt;
	    // dead
	    if (dead) {
		// drop to floor
		xSpeed = 0;
		if (!grounded) ySpeed += gravity * dt;
		ySpeed = Math.min (ySpeed, maxSpeed);
		// countdown to respawn
		deadTimer += dt;
		if (deadTimer > deadDur) {
		    // reset coins & enemies
		    //coinsCollected = 0;
		    //for (int i = 0; i < allCoins.size (); i++) allCoins.get (i).Reset ();
		    for (int i = 0; i < enemies.size (); i++) enemies.get (i).Reset ();
		    // respawn
		    Reset ();
		    return;
		}
	    }
	    else {
		// temp invincible after damage
		if (invincible > 0f) invincible -= dt;
		if (ySpeed >= 0f) justHurt = false;
		// determine walking
		int w = 0;
		if (!ended) {
		    w = pressLeft ? -1 : (pressRight ? 1 : 0);
		    xSpeed = walkSpeed * w;
		}
		else xSpeed = 0;
		if (w == 0) {
		    // move animation towards middle frame
		    anim.timer += anim.speed * (anim.timer > .5f ? -1 : 1) * dt;
		    anim.direction = 1f;
		}
		else {
		    // face towards walking direction
		    if (prevWalk == 0) anim.timer = .75f; // to fix starting jitter
		    if (w > 0) xScale = Math.abs (xScale);
		    else if (w < 0) xScale = -Math.abs (xScale);
		    // perform "ping-pong" animation for walk
		    anim.AnimatePingPong (dt);
		}
		prevWalk = w;
	    }
	    // call Character's Loop()
	    super.Loop (dt);
	    // get touched cells and do the special checks
	    UpdateTouchedCells ();
	    LavaKillsYou ();
	    if (!ended) CheckIfReachedEnd ();
	    // stay in boundaries
	    x = Math.max (-bboxX1, Math.min (stageWidth * cell  - bboxX2, x));
	    if (!dead && y + bboxY2 >= stageHeight * cell) Die ();
	}
	
	public void Jump () {
	    if (ended) {
		if (textTypewriter == 1f) LoadNextStage ();
	    }
	    else if (!dead && grounded) ySpeed = jumpSpeed;
	}

	// find region of cells which we touch
	void UpdateTouchedCells () {
	    
	    touchCellMinX = Math.max (0, Math.min (stageWidth - 1,  (int)((x + bboxX1) / (float)cell)));
	    touchCellMinY = Math.max (0, Math.min (stageHeight - 1, (int)((y + bboxY1) / (float)cell)));
	    touchCellMaxX = Math.max (0, Math.min (stageWidth - 1,  (int)((x + bboxX2) / (float)cell)));
	    touchCellMaxY = Math.max (0, Math.min (stageHeight - 1, (int)((y + bboxY2) / (float)cell)));
	}
	
	// die if touching lava, and sink slowly in it
	void LavaKillsYou () {
	    
	    // find whether we are touching lava
	    var inLava = false;
	    for (int j = touchCellMinY; j <= touchCellMaxY; j++) {
		for (int i = touchCellMinX; i <= touchCellMaxX; i++) {
		    if (stageCells[i][j] == tileLava ||
			    (stageCells[i][j] == tileLavaTop && y + bboxY2 > cell * (j + .3f))) {
			inLava = true;
			break;
		    }
		}
	    }
	    // if touching, die and sink slowly
	    if (inLava) {
		burntByLava = true;
		if (dead) ySpeed = Math.min (ySpeed, lavaSinkSpeed);
		else Die ();
	    }
	}
	
	// end stage if touched end portal
	void CheckIfReachedEnd () {
	    
	    for (int j = touchCellMinY; j <= touchCellMaxY; j++) {
		for (int i = touchCellMinX; i <= touchCellMaxX; i++) {
		    if (stageCells[i][j] == tileEnd) {
			ended = true;
			break;
		    }
		}
	    }
	}

	void Flash () {
	    flashTimer = flashDur;
	}
	
	public void Damage () {
	    
	    if (dead || invincible > 0f) return;
	    Flash ();
	    ySpeed = hurtJumpSpeed;
	    health--;
	    if (health <= 0) Die ();
	    else {
		invincible = invincibleDur;
		justHurt = true;
	    }
	}
	
	public void Die () {
	    
	    if (dead) return;
	    Flash ();
	    ySpeed = jumpSpeed;
	    //timescale = .25f; // slowmotion
	    dead = true;
	}
	
	public void LoadNextStage () {
	    
	    if (curStage >= stageFiles.length - 1) return;
	    // reset coins & enemies
	    coinsCollected = 0;
	    allCoins.clear ();
	    enemies.clear ();
	    // load next stage
	    curStage++;
	    loadStage (stageFiles[curStage]);
	    // respawn
	    Reset ();
	}
	
	@Override
	public void Draw (Graphics2D g) {
	    
	    // calculate position and frame to draw
	    float x1 = x - cell/2f * xScale;
	    float y1 = y - cell/2f * yScale;
	    int sx1 = (int)(anim.timer * anim.frames) * cell;
	    int sy1 = 0;
	    if (flashTimer > 0) {
		// flash damage
		sx1 = 0;
		sy1 = cell;
	    }
	    else if (dead) {
		// dead
		if (grounded || burntByLava) {
		    sx1 = cell * 2;
		    sy1 = cell * 2;
		}
		else {
		    sx1 = cell;
		    sy1 = cell;
		}
	    }
	    else {
		// blink while invincible
		if (invincible > 0f && (invincible * invincibleFlashFrq) % 1f < .25f)
		    return;
		if (!grounded) {
		    // jumping / falling
		    sy1 = cell;
		    sx1 = (ySpeed < 0 && !justHurt ? 2 : 1) * cell;
		}
		else if (ended) {
		    // fanfare
		    sx1 = cell;
		    sy1 = cell * 2;
		}
	    }
	    // draw
	    var bak = g.getTransform ();
	    g.translate (x1, y1);
	    g.drawImage (imgPlayer, 0, 0, (int)(cell * xScale), (int)(cell * yScale), sx1, sy1, sx1 + cell, sy1 + cell, null);
	    g.setTransform (bak);
	}
    }
    
    // enemy class
    List<Enemy> enemies = new ArrayList<> ();
    public class Enemy extends Character {
	
	// start pos
	float myStartX;
	float myStartY;
	// motion
	float walkSpeed = 32f;
	float lingerDur = 2f;
	float lingerTimer = 1f;
	int moveDir = 1;
	boolean turnOnEdge = true;
	// animation
	AnimData anim = new AnimData (0f, 2f, 1f, 3);
	// keep animating even when not moving?
	boolean locomotion = false;
	// Y value of image on spritesheet
	int imgSourceY = 0;
    
	public Enemy (float x, float y) {
	    
	    this.x = x;
	    this.y = y;
	    myStartX = x;
	    myStartY = y;
	    bboxX1 -= 1;
	    bboxX2 += 1;
	    xScale = 1f;
	    enemies.add (this);
	}
	
	public void Reset () {
	    
	    x = myStartX;
	    y = myStartY;
	    xSpeed = 0;
	    ySpeed = 0;
	    anim.timer = 0f;
	    lingerTimer = 1f;
	    moveDir = 1;
	    xScale = 1f;
	}
	
	@Override
	public void Loop (float dt) {
	    
	    // determine walking
	    int w = 0;
	    if (lingerTimer > 0f) lingerTimer -= dt;
	    else w = moveDir;
	    xSpeed = walkSpeed * w;
	    if (w != 0) {
		// face towards walking direction
		if (w > 0) xScale = Math.abs (xScale);
		else if (w < 0) xScale = -Math.abs (xScale);
		// if on edge or hit wall, stop and turn
		if (touchedWall || IsOnEdge (0f)) {
		    lingerTimer = lingerDur;
		    moveDir = -moveDir;
		    touchedWall = false;
		}
	    }
	    if (locomotion || w != 0) {
		// perform "ping-pong" animation for walk
		anim.AnimatePingPong (dt);
	    }
	    else {
		// move animation towards start frame
		anim.timer += anim.speed * (anim.timer > .5f / anim.frames ? -1 : 1) * dt;
		anim.direction = 1f;
	    }
	    // call Character's Loop()
	    super.Loop (dt);
	    // kill player on touch
	    KillPlayerOnTouch ();
	}

	void CharacterLoop (float dt) {
	    super.Loop (dt);
	}
	
	void KillPlayerOnTouch () {
	    
	    if (!player.dead && player.invincible <= 0f && IsTouchingPlayer ())
		player.Damage ();
	}
	
	boolean IsTouchingPlayer () {
	
	    var myXMid = x + bboxX2 + bboxX1;
	    var myYMid = y + bboxY2 + bboxY1;
	    var myXExtent = x + bboxX2 - myXMid;
	    var myYExtent = y + bboxY2 - myYMid;
	    var pXMid = player.x + player.bboxX2 + player.bboxX1;
	    var pYMid = player.y + player.bboxY2 + player.bboxY1;
	    var pXExtent = player.x + player.bboxX2 - pXMid;
	    var pYExtent = player.y + player.bboxY2 - pYMid;
	    return Math.abs (myXMid - pXMid) < myXExtent + pXExtent
		    && Math.abs (myYMid - pYMid) < myYExtent + pYExtent;
	}
	
	// check if there is an edge, at most "dx" pixels ahead of us
	boolean IsOnEdge (float dx) {
	    
	    if (!grounded) return false;
	    for (int i = 0; i <= dx; i += cell) {
		float xx = x + (moveDir > 0 ? bboxX2 : bboxX1) + i * moveDir;
		float yy = y + bboxY2 + 1;
		if(!cellIsSolid ((int)(xx / cell), (int)(yy / cell))) return true;		
	    }
	    return false;
	}
	
	@Override
	public void Draw (Graphics2D g) {
	    
	    // calculate position and frame to draw
	    float x1 = x - cell/2f * xScale;
	    float y1 = y - cell/2f * yScale;
	    int sx1 = (int)(anim.timer * anim.frames) * cell;
	    int sy1 = imgSourceY;
	    // draw
	    var bak = g.getTransform ();
	    g.translate (x1, y1);
	    g.drawImage (imgEnemies, 0, 0, (int)(cell * xScale), (int)(cell * yScale), sx1, sy1, sx1 + cell, sy1 + cell, null);
	    g.setTransform (bak);
	}
    }
    
    // slime enemy class (exact copy of Enemy class)
    public class Slime extends Enemy {
	public Slime (float x, float y) {
	    super (x, y);
	}
    }

    // bat enemy class
    public class Bat extends Enemy {
	public Bat (float x, float y) {
	    super (x, y);
	    // don't avoid edges
	    turnOnEdge = false;
	    walkSpeed = 48f;
	    // floating
	    gravity = 0f;
	    grounded = false;
	    // do not linger before turning
	    lingerDur = 0f;
	    // faster animation
	    anim = new AnimData (0f, 3f, 1f, 3);
	    // animate even when not moving
	    locomotion = true;
	    // spritesheet Y of our image
	    imgSourceY = 2 * cell;
 	}
    }

    // imp enemy class
    public class Imp extends Enemy {
	
	// jump
	float jumpSpeed = -120f;
	// distance to check ahead for edge before jumping
	int distOfEdgeCheck = cell * 2;
	// crouch before & after jump
	float crouchDur = .2f;
	float crouchTimer = crouchDur;
	// NOTE: anim and locomotion variables are not used here
	
	public Imp (float x, float y) {
	    super (x, y);
	    walkSpeed = 64f;
	    lingerDur = .8f;
	    imgSourceY = cell;
 	}

	@Override
	public void Reset () {
	    super.Reset ();
	    crouchTimer = crouchDur;
	}

	@Override
	public void Loop (float dt) {
	    
	    // determine walking
	    int w = 0;
	    if (crouchTimer > 0f) {
		crouchTimer -= dt;
		// jump on end of preparing crouch
		if (crouchTimer <= 0f && lingerTimer <= 0f) ySpeed = jumpSpeed;
	    }
	    else if (lingerTimer > 0f) {
		lingerTimer -= dt;
		// crouch again after lingering (and before jumping)
		if (lingerTimer <= 0f) crouchTimer = crouchDur;
	    }
	    else w = moveDir;
	    xSpeed = walkSpeed * w;
	    // face towards walking direction
	    if (lingerTimer <= 0f) {
		if (moveDir > 0) xScale = Math.abs (xScale);
		else if (moveDir < 0) xScale = -Math.abs (xScale);
	    }
	    // if moving and have landed, stop and linger
	    if (w != 0 && grounded && ySpeed >= 0) {
		crouchTimer = crouchDur;
		lingerTimer = lingerDur;
		// if on edge or wall, turn around
		if (touchedWall || IsOnEdge (distOfEdgeCheck)) {
		    moveDir = -moveDir;
		    touchedWall = false;
		}
	    }
	    else if (!grounded) touchedWall = false;
	    // call Character's Loop()
	    CharacterLoop (dt);
	    // kill player on touch
	    KillPlayerOnTouch ();
	}
	
	@Override
	public void Draw (Graphics2D g) {
	    
	    // calculate position and frame to draw
	    float x1 = x - cell/2f * xScale;
	    float y1 = y - cell/2f * yScale;
	    int sx1 = cell;
	    int sy1 = imgSourceY;
	    if (crouchTimer > 0f) sx1 = 2 * cell;
	    else if (grounded) sx1 = 0;
	    // draw
	    var bak = g.getTransform ();
	    g.translate (x1, y1);
	    g.drawImage (imgEnemies, 0, 0, (int)(cell * xScale), (int)(cell * yScale), sx1, sy1, sx1 + cell, sy1 + cell, null);
	    g.setTransform (bak);
	}
    }

    // common animation for all coins
    AnimData coinAnim = new AnimData (0f, 1.75f, 1f, 3);
    // coins lists
    List<Coin> allCoins = new ArrayList<> (); // all coins
    List<Coin> activeCoins = new ArrayList<> (); // visible coins only
    
    public final class Coin {
	
	// position
	float x, y;
	// distance from player at which coin is collected, also used for culling
	final float bbox = 12f;
	// fade when collected
	float fade = 0f;
	public final float fadeSpeed = 5f;
	public final float fadeAscend = 20f;
	
	public Coin (float x, float y) {
	    this.x = x;
	    this.y = y;
	    Init ();
	}
	
	void Init () {
	    allCoins.add (this);
	    activeCoins.add (this);
	}
	
	void Collect () {
	    fade = .001f;
	    player.coinsCollected++;
	}
	
	public void Reset () {
	    fade = 0f;
	    if (!activeCoins.contains (this)) activeCoins.add (this);
	}
	
	public void Loop (float dt) {
	    
	    if (fade > 0) {
		// fade
		fade += fadeSpeed * dt;
		if (fade >= 1f) {
		    fade = 1f;
		    activeCoins.remove (this);
		}
	    }
	    else {
		// collect
		if (!player.dead && Math.abs (x - player.x) < bbox
			&& Math.abs (y - player.y) < bbox) Collect ();
	    }
	}

	public void Draw (Graphics2D g) {
	    
	    // cull if not in view
	    var d = gamePanel.getSize ();
	    var w2 = d.width / 2f / scale;
	    var h2 = d.height / 2f / scale;
	    if (x + bbox < xCamera - w2 || y + bbox < yCamera - h2 ||
		    x - bbox > xCamera + w2 || y - bbox > yCamera + h2) return;
	    // calculate position and frame to draw
	    float x1 = x - cell/2f;
	    float y1 = y - cell/2f;
	    int sx1 = cell + (int)(coinAnim.timer * coinAnim.frames) * cell;
	    int sy1 = cell;
	    if (fade > 0) {
		sx1 = 0;
		sy1 = cell;
	    }
	    // draw including fade transform
	    var bak = g.getTransform ();
	    g.translate (x1, y1 - fade * fadeAscend);
	    g.drawImage (imgItems, 0, 0, cell, cell, sx1, sy1, sx1 + cell, sy1 + cell, null);
	    g.setTransform (bak);
	}
    }

    // general animation data class
    public class AnimData {
	
	public float timer;
	public float speed = 1f;
	public float direction = 1f;
	public int frames = 1;
	
	public AnimData (float timer, float speed, float direction, int frames) {
	    this.timer = timer;
	    this.speed = speed;
	    this.direction = direction;
	    this.frames = frames;
	}

        // perform "ping-pong" animation
	public void AnimatePingPong (float dt) {
	    
	    timer += speed * direction * dt;
	    if (direction > 0 && timer > 1f - .5f / frames) {
		direction = -direction;
		timer = 1f - .5f / frames;
	    } else if (direction < 0 && timer < .5f / frames) {
		direction = -direction;
		timer = .5f / frames;
	    }
	}

	// perform sequential animation
	public void AnimateSequential (float dt) {
	    
	    timer += speed * direction * dt;
	    if (direction > 0 && timer >= 1f) {
		timer -= 1f;
	    } else if (direction < 0 && timer < 0f) {
		timer += 1f;
	    }
	}
    }

    // Game Frame
    public class Frame extends JFrame {

	// timing variables
	long lastTime = 0;
	long accumulator = 0;
	long accumulatorDraw = 0;

	// start function
	public void start () {

	    // window size & other settings
	    setSize (windowWidth, windowHeight);
	    setLocationRelativeTo (null);
	    //setResizable (false);
	    setFocusable (true);
	    // add listener to quit program on window close
	    setDefaultCloseOperation (JFrame.EXIT_ON_CLOSE);
	    
	    // make display panel
	    gamePanel = new Panel ();
	    gamePanel.setupInputs ();
	    gamePanel.setBackground (bgColor);
	    getContentPane().add(gamePanel);
	    setVisible (true);
	    gamePanel.requestFocusInWindow ();
	    
	    // initialize
	    game.Initialize ();
	    
	    // begin loop
	    executeLoop ();
	}
	
	// begin loop
	void executeLoop () {
	    
	    // initialize timers
	    lastTime = System.nanoTime();
	    accumulator = 0;
	    accumulatorDraw = 0;
	    
	    // loop with killswitch
	    Thread loop = new Thread () {
		public void run () {
		    
		    while (running) {			

			// calculate time delta
			long now = System.nanoTime ();
			accumulator += now - lastTime;
			accumulatorDraw += now - lastTime;
			lastTime = now;

			if (accumulator > nsMaxDelayTolerated) {
			    // skip fixedLoop if above tolerated delay (possibly computer hiccup)
			    accumulator = 0;
			}
			else {
			    // perform fixed-timestep loop as many times as necessary
			    while (accumulator >= nsPerTick) {
				time += secPerTick * timescale;
				game.Loop (secPerTick * timescale);
				accumulator -= nsPerTick;
			    }
			}

			// insert waiting time
			Thread.yield();
			try {
			    Thread.sleep (1);
			} catch (InterruptedException ex) {
			    System.out.println (ex.getMessage ());
			}

			// redraw at required intervals
			if (accumulatorDraw > nsPerDraw) {
			    gamePanel.repaint ();
			    accumulatorDraw %= nsPerDraw;
			}
		    }
		}
	    };
	    loop.start ();
	}

    }

    // panel used for drawing game
    public class Panel extends JPanel {

	InputMap inputMap;
	ActionMap actionMap; 
	
	public void setupInputs () {
	    
	    // get keybinding maps
	    inputMap = getInputMap (JComponent.WHEN_IN_FOCUSED_WINDOW);
	    actionMap = getActionMap ();
	    // pause binding
	    inputMap.put(KeyStroke.getKeyStroke (KeyEvent.VK_ESCAPE, 0), "pause");
	    actionMap.put ("pause", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    paused = !paused;
		}
	    });
	    // left binding
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "leftUp");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "left");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "leftUp");
	    actionMap.put ("left", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    pressLeft = true;
		}
	    });
	    actionMap.put ("leftUp", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    pressLeft = false;
		}
	    });
	    // right binding
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "rightUp");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "right");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "rightUp");
	    actionMap.put ("right", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    pressRight = true;
		}
	    });
	    actionMap.put ("rightUp", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    pressRight = false;
		}
	    });
	    // jump binding
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "jump");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "jump");
	    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "jump");
	    actionMap.put ("jump", new AbstractAction () {
		public void actionPerformed (ActionEvent e) {
		    player.Jump ();
		}
	    });
	}
	
	@Override
	public void paintComponent (Graphics g) {
	    
	    // perform original drawing function
	    super.paintComponent (g);
	    // execute game drawing function
	    Draw (g, getSize ());
	}
    }

    // function to find the index of an element 
    public static int findIndex (int arr[], int t) {

	// if array is null, return -1
	if (arr == null) {
	    return -1;
	}

	// parse the array 
	for (int i = 0; i < arr.length; i++) {
	    // if the i-th element is t, return the index 
	    if (arr[i] == t) {
		return i;
	    }
	}
	// not found, return -1
	return -1;
    }
}
