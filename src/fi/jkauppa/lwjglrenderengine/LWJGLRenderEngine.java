package fi.jkauppa.lwjglrenderengine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.Version;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL46;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class LWJGLRenderEngine {
	private long window;
    GLCapabilities caps;
    GLFWKeyCallback keyCallback;
    Callback debugProc;

	private int vao;
	private int quadProgram;
	private int quadProgram_inputPosition;
	private int quadProgram_inputTextureCoords;
	
	private int framebufferwidth = 1920;
	private int framebufferheight = 1080;
	private int framebufferlength = framebufferwidth*framebufferheight;
	private int[] framebuffer = new int[framebufferlength];

	public void run() {
		System.out.println("Hello LWJGL " + Version.getVersion() + "!");

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
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 0);
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
		window = GLFW.glfwCreateWindow(framebufferwidth, framebufferheight, "LWJGL Render Engine v0.1.0", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {throw new RuntimeException("Failed to create the GLFW window");}
		GLFW.glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
			if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
				GLFW.glfwSetWindowShouldClose(window, true);
		});
		try ( MemoryStack stack = MemoryStack.stackPush() ) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*
			GLFW.glfwGetWindowSize(window, pWidth, pHeight);
			GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
			GLFW.glfwSetWindowPos(
				window,
				(vidmode.width() - pWidth.get(0)) / 2,
				(vidmode.height() - pHeight.get(0)) / 2
			);
		}

		GLFW.glfwMakeContextCurrent(window);
		GLFW.glfwSwapInterval(1);
		GLFW.glfwShowWindow(window);

		caps = GL.createCapabilities();
		debugProc = GLUtil.setupDebugMessageCallback();
		
		for (int y=0;y<100;y++) {
			for (int x=0;x<framebufferwidth;x++) {
				framebuffer[y*framebufferwidth+x] = 0xff0000ff;
			}
		}
		
		try {
			createTexture();
        	createQuadProgram();
        	createFullScreenQuad();
		} catch(Exception ex){System.out.println(ex.toString());}
	}

	private void loop() {
		GL46.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		while ( !GLFW.glfwWindowShouldClose(window) ) {
			GL46.glClear(GL46.GL_COLOR_BUFFER_BIT | GL46.GL_DEPTH_BUFFER_BIT);
			GL46.glViewport(0, 0, framebufferwidth, framebufferheight);
			render();
			GLFW.glfwSwapBuffers(window);
			GLFW.glfwPollEvents();
		}
	}

    void render() {
    	GL46.glClear(GL46.GL_COLOR_BUFFER_BIT);
    	GL46.glUseProgram(quadProgram);
    	GL46.glBindVertexArray(vao);
    	GL46.glDrawArrays(GL46.GL_TRIANGLES, 0, 6);
    	GL46.glBindVertexArray(0);
    	GL46.glUseProgram(0);
    }
	
	public static void main(String[] args) {
		new LWJGLRenderEngine().run();
	}
	
    private void createQuadProgram() throws IOException {
        int program = GL46.glCreateProgram();
        int vshader = createShader("res/glshaders/texturedquad.vs", GL46.GL_VERTEX_SHADER, null);
        int fshader = createShader("res/glshaders/texturedquad.fs", GL46.GL_FRAGMENT_SHADER, null);
        GL46.glAttachShader(program, vshader);
        GL46.glAttachShader(program, fshader);
        GL46.glLinkProgram(program);
        int linked = GL46.glGetProgrami(program, GL46.GL_LINK_STATUS);
        String programLog = GL46.glGetProgramInfoLog(program);
        if (programLog.trim().length() > 0)
            System.err.println(programLog);
        if (linked == 0)
            throw new AssertionError("Could not link program");
        GL46.glUseProgram(program);
        int texLocation = GL46.glGetUniformLocation(program, "tex");
        GL46.glUniform1i(texLocation, 0);
        quadProgram_inputPosition = GL46.glGetAttribLocation(program, "position");
        quadProgram_inputTextureCoords = GL46.glGetAttribLocation(program, "texCoords");
        GL46.glUseProgram(0);
        this.quadProgram = program;
    }

    void createFullScreenQuad() {
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

    void createTexture() throws IOException {
        int id = GL46.glGenTextures();
        GL46.glBindTexture(GL46.GL_TEXTURE_2D, id);
        GL46.glTexParameteri(GL46.GL_TEXTURE_2D, GL46.GL_TEXTURE_MIN_FILTER, GL46.GL_LINEAR);
        GL46.glTexParameteri(GL46.GL_TEXTURE_2D, GL46.GL_TEXTURE_MAG_FILTER, GL46.GL_LINEAR);
        GL46.glTexImage2D(GL46.GL_TEXTURE_2D, 0, GL46.GL_RGBA8, framebufferwidth, framebufferheight, 0, GL46.GL_RGBA, GL46.GL_UNSIGNED_INT_8_8_8_8, framebuffer);
    }

    public static int createShader(String resource, int type, String version) throws IOException {
        int shader = GL46.glCreateShader(type);

        ByteBuffer source = IOUtils.ioResourceToByteBuffer(resource, 8192);

        if ( version == null ) {
            PointerBuffer strings = BufferUtils.createPointerBuffer(1);
            IntBuffer lengths = BufferUtils.createIntBuffer(1);

            strings.put(0, source);
            lengths.put(0, source.remaining());

            GL46.glShaderSource(shader, strings, lengths);
        } else {
            PointerBuffer strings = BufferUtils.createPointerBuffer(2);
            IntBuffer lengths = BufferUtils.createIntBuffer(2);

            ByteBuffer preamble = MemoryUtil.memUTF8("#version " + version + "\n", false);

            strings.put(0, preamble);
            lengths.put(0, preamble.remaining());

            strings.put(1, source);
            lengths.put(1, source.remaining());

            GL46.glShaderSource(shader, strings, lengths);
        }

        GL46.glCompileShader(shader);
        int compiled = GL46.glGetShaderi(shader, GL46.GL_COMPILE_STATUS);
        String shaderLog = GL46.glGetShaderInfoLog(shader);
        if (shaderLog.trim().length() > 0) {
            System.err.println(shaderLog);
        }
        if (compiled == 0) {
            throw new AssertionError("Could not compile shader");
        }
        return shader;
    }    

    private class IOUtils {
        private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
            ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
            buffer.flip();
            newBuffer.put(buffer);
            return newBuffer;
        }

        public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
            ByteBuffer buffer;
            URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
            if (url == null)
                throw new IOException("Classpath resource not found: " + resource);
            File file = new File(url.getFile());
            if (file.isFile()) {
                FileInputStream fis = new FileInputStream(file);
                FileChannel fc = fis.getChannel();
                buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                fc.close();
                fis.close();
            } else {
                buffer = BufferUtils.createByteBuffer(bufferSize);
                InputStream source = url.openStream();
                if (source == null)
                    throw new FileNotFoundException(resource);
                try {
                    byte[] buf = new byte[8192];
                    while (true) {
                        int bytes = source.read(buf, 0, buf.length);
                        if (bytes == -1)
                            break;
                        if (buffer.remaining() < bytes)
                            buffer = resizeBuffer(buffer, Math.max(buffer.capacity() * 2, buffer.capacity() - buffer.remaining() + bytes));
                        buffer.put(buf, 0, bytes);
                    }
                    buffer.flip();
                } finally {
                    source.close();
                }
            }
            return buffer;
        }
    }
}
