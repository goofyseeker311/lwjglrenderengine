package fi.jkauppa.lwjglrenderengine;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL46;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryUtil;

public class LWJGLRenderEngine {
	private long window;
    GLCapabilities caps;
    Callback debugProc;

	private int vao;
	private int tex;
	private int quadProgram;
	private int quadProgram_inputPosition;
	private int quadProgram_inputTextureCoords;
	
	private int framebufferwidth = 1920;
	private int framebufferheight = 1080;
	private int framebufferlength = framebufferwidth*framebufferheight;
	private int[] framebuffer = new int[framebufferlength];

	public void run() {
		init();
		loop();
		Callbacks.glfwFreeCallbacks(window);
		GLFW.glfwDestroyWindow(window);
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
	}

	private void init() {
		GLFWErrorCallback.createPrint(System.err).set();
		if ( !GLFW.glfwInit() ) {throw new IllegalStateException("Unable to initialize GLFW");}
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
		window = GLFW.glfwCreateWindow(framebufferwidth, framebufferheight, "LWJGL Render Engine v0.2.0", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {throw new RuntimeException("Failed to create the GLFW window");}
		GLFW.glfwMakeContextCurrent(window);
		GLFW.glfwSwapInterval(1);
		GLFW.glfwShowWindow(window);
		caps = GL.createCapabilities();
		
		for (int y=0;y<100;y++) {for (int x=0;x<framebufferwidth;x++) {framebuffer[y*framebufferwidth+x] = 0xff0000ff;}}
		
		tex = createTexture();
        createQuadProgram();
        createFullScreenQuad();
	}

	private void loop() {
		GL46.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		while ( !GLFW.glfwWindowShouldClose(window) ) {
			GL46.glClear(GL46.GL_COLOR_BUFFER_BIT | GL46.GL_DEPTH_BUFFER_BIT);
			updateTexture(tex);
			GL46.glViewport(0, 0, framebufferwidth, framebufferheight);
	    	GL46.glUseProgram(quadProgram);
	    	GL46.glBindVertexArray(vao);
	    	GL46.glDrawArrays(GL46.GL_TRIANGLES, 0, 6);
	    	GL46.glBindVertexArray(0);
	    	GL46.glUseProgram(0);
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
		}
	}
	
	public static void main(String[] args) {
		new LWJGLRenderEngine().run();
	}
	
    private void createQuadProgram() {
        int program = GL46.glCreateProgram();
        int vshader = createShader("res/glshaders/texturedquad.vs", GL46.GL_VERTEX_SHADER, true);
        int fshader = createShader("res/glshaders/texturedquad.fs", GL46.GL_FRAGMENT_SHADER, true);
        GL46.glAttachShader(program, vshader);
        GL46.glAttachShader(program, fshader);
        GL46.glLinkProgram(program);
        int linked = GL46.glGetProgrami(program, GL46.GL_LINK_STATUS);
        String programLog = GL46.glGetProgramInfoLog(program);
        if (programLog.trim().length() > 0) {System.err.println(programLog);}
        if (linked == 0) {throw new AssertionError("Could not link program");}
        GL46.glUseProgram(program);
        int texLocation = GL46.glGetUniformLocation(program, "tex");
        GL46.glUniform1i(texLocation, 0);
        quadProgram_inputPosition = GL46.glGetAttribLocation(program, "position");
        quadProgram_inputTextureCoords = GL46.glGetAttribLocation(program, "texCoords");
        GL46.glUseProgram(0);
        this.quadProgram = program;
    }

    private void createFullScreenQuad() {
        vao = GL46.glGenVertexArrays();
        GL46.glBindVertexArray(vao);
        int positionVbo = GL46.glGenBuffers();
        FloatBuffer fb = BufferUtils.createFloatBuffer(2 * 6);
        fb.put(-1.0f).put(-1.0f);
        fb.put(1.0f).put(-1.0f);
        fb.put(1.0f).put(1.0f);
        fb.put(1.0f).put(1.0f);
        fb.put(-1.0f).put(1.0f);
        fb.put(-1.0f).put(-1.0f);
        fb.flip();
        GL46.glBindBuffer(GL46.GL_ARRAY_BUFFER, positionVbo);
        GL46.glBufferData(GL46.GL_ARRAY_BUFFER, fb, GL46.GL_STATIC_DRAW);
        GL46.glVertexAttribPointer(quadProgram_inputPosition, 2, GL46.GL_FLOAT, false, 0, 0L);
        GL46.glEnableVertexAttribArray(quadProgram_inputPosition);
        int texCoordsVbo = GL46.glGenBuffers();
        fb = BufferUtils.createFloatBuffer(2 * 6);
        fb.put(0.0f).put(1.0f);
        fb.put(1.0f).put(1.0f);
        fb.put(1.0f).put(0.0f);
        fb.put(1.0f).put(0.0f);
        fb.put(0.0f).put(0.0f);
        fb.put(0.0f).put(1.0f);
        fb.flip();
        GL46.glBindBuffer(GL46.GL_ARRAY_BUFFER, texCoordsVbo);
        GL46.glBufferData(GL46.GL_ARRAY_BUFFER, fb, GL46.GL_STATIC_DRAW);
        GL46.glVertexAttribPointer(quadProgram_inputTextureCoords, 2, GL46.GL_FLOAT, true, 0, 0L);
        GL46.glEnableVertexAttribArray(quadProgram_inputTextureCoords);
        GL46.glBindBuffer(GL46.GL_ARRAY_BUFFER, 0);
        GL46.glBindVertexArray(0);
    }

    private int createTexture() {
        int id = GL46.glGenTextures();
        GL46.glBindTexture(GL46.GL_TEXTURE_2D, id);
        GL46.glTexParameteri(GL46.GL_TEXTURE_2D, GL46.GL_TEXTURE_MIN_FILTER, GL46.GL_NEAREST);
        GL46.glTexParameteri(GL46.GL_TEXTURE_2D, GL46.GL_TEXTURE_MAG_FILTER, GL46.GL_NEAREST);
        GL46.glTexImage2D(GL46.GL_TEXTURE_2D, 0, GL46.GL_RGBA8, framebufferwidth, framebufferheight, 0, GL46.GL_RGBA, GL46.GL_UNSIGNED_INT_8_8_8_8, MemoryUtil.NULL);
        return id;
    }
    
    private void updateTexture(int id) {
        GL46.glBindTexture(GL46.GL_TEXTURE_2D, id);
        GL46.glTexSubImage2D(GL46.GL_TEXTURE_2D, 0, 0, 0, framebufferwidth, framebufferheight, GL46.GL_RGBA, GL46.GL_UNSIGNED_INT_8_8_8_8, framebuffer);
    }
    
    private int createShader(String resource, int type, boolean loadresourcefromjar) {
        int shader = GL46.glCreateShader(type);
        String sourceShader = loadShader(resource, loadresourcefromjar);
        ByteBuffer source = BufferUtils.createByteBuffer(8192);
        source.put(sourceShader.getBytes()).rewind();
        PointerBuffer strings = BufferUtils.createPointerBuffer(1);
        IntBuffer lengths = BufferUtils.createIntBuffer(1);
        strings.put(0, source);
        lengths.put(0, source.remaining());
        GL46.glShaderSource(shader, strings, lengths);
        GL46.glCompileShader(shader);
        int compiled = GL46.glGetShaderi(shader, GL46.GL_COMPILE_STATUS);
        String shaderLog = GL46.glGetShaderInfoLog(shader);
        if (shaderLog.trim().length() > 0) {System.err.println(shaderLog);}
        if (compiled == 0) {throw new AssertionError("Could not compile shader");}
        return shader;
    }    
    
	private String loadShader(String filename, boolean loadresourcefromjar) {
		String k = null;
		if (filename!=null) {
			try {
				File textfile = new File(filename);
				BufferedInputStream textfilestream = null;
				if (loadresourcefromjar) {
					textfilestream = new BufferedInputStream(ClassLoader.getSystemClassLoader().getResourceAsStream(textfile.getPath().replace(File.separatorChar, '/')));
				}else {
					textfilestream = new BufferedInputStream(new FileInputStream(textfile));
				}
				k = new String(textfilestream.readAllBytes());
				textfilestream.close();
			} catch (Exception ex) {ex.printStackTrace();}
		}
		return k;
	}
    
}
