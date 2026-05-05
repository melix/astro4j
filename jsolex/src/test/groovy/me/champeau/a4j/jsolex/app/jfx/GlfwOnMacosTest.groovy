/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.app.jfx

import javafx.application.Platform
import me.champeau.a4j.jsolex.app.jfx.gl.GlMatrix
import me.champeau.a4j.jsolex.app.jfx.gl.ShaderProgram
import me.champeau.a4j.jsolex.app.jfx.gl.SphereMeshBuilder
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryUtil
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Requires({ System.getProperty('os.name').toLowerCase().contains('mac') })
class GlfwOnMacosTest extends Specification {

    @Shared
    private List<String> errors = Collections.synchronizedList([])

    @Shared
    private GLFWErrorCallback errorCallback

    def setupSpec() {
        Configuration.GLFW_LIBRARY_NAME.set('glfw_async')
        Configuration.GLFW_CHECK_THREAD0.set(false)

        errorCallback = GLFWErrorCallback.create({ error, description ->
            def desc = MemoryUtil.memUTF8Safe(description)
            errors.add(String.format('0x%08X: %s', error, desc))
            System.err.println("[GLFW] error 0x${Integer.toHexString(error)}: $desc")
        })
        GLFW.glfwSetErrorCallback(errorCallback)

        try {
            Platform.startup({})
        } catch (IllegalStateException ignore) {
        }

        def latch = new CountDownLatch(1)
        Platform.runLater { latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
    }

    def cleanupSpec() {
        try {
            GLFW.glfwTerminate()
        } catch (Throwable ignore) {
        }
        if (errorCallback != null) {
            GLFW.glfwSetErrorCallback(null)
            errorCallback.free()
        }
        Platform.exit()
    }

    def "volume shader compiles, links and produces non-black output under GL 3.2 core"() {
        when:
        def result = runOffMain {
            if (!GLFW.glfwInit()) {
                return 'glfwInit failed'
            }
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

            long window = GLFW.glfwCreateWindow(1, 1, '', 0L, 0L)
            if (window == 0L) {
                return 'glfwCreateWindow returned 0'
            }
            try {
                GLFW.glfwMakeContextCurrent(window)
                GL.createCapabilities()

                int fbo = GL30.glGenFramebuffers()
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo)
                int colorTex = GL11.glGenTextures()
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null)
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0)
                int depthRb = GL30.glGenRenderbuffers()
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb)
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, 64, 64)
                GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb)
                int fbStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                if (fbStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    return "fbo incomplete: 0x${Integer.toHexString(fbStatus)}".toString()
                }
                GL11.glViewport(0, 0, 64, 64)
                GL11.glEnable(GL11.GL_DEPTH_TEST)

                def shader = new ShaderProgram()
                shader.compile(
                        ShaderProgram.loadShaderSource('/me/champeau/a4j/jsolex/app/shaders/volume.vert'),
                        ShaderProgram.loadShaderSource('/me/champeau/a4j/jsolex/app/shaders/volume.frag'))

                int vao = GL30.glGenVertexArrays()
                GL30.glBindVertexArray(vao)
                int vbo = GL15.glGenBuffers()
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
                float[] quad = [-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f] as float[]
                def quadBuf = BufferUtils.createFloatBuffer(quad.length)
                quadBuf.put(quad).flip()
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuf, GL15.GL_STATIC_DRAW)
                int posLoc = GL20.glGetAttribLocation(shader.programId, 'position')
                GL20.glEnableVertexAttribArray(posLoc)
                GL20.glVertexAttribPointer(posLoc, 2, GL11.GL_FLOAT, false, 0, 0L)

                int volTex = GL11.glGenTextures()
                GL13.glActiveTexture(GL13.GL_TEXTURE0)
                GL12.glBindTexture(GL12.GL_TEXTURE_3D, volTex)
                def volBuf = BufferUtils.createFloatBuffer(4 * 4 * 4)
                for (int i = 0; i < 4 * 4 * 4; i++) {
                    volBuf.put(0.7f)
                }
                volBuf.flip()
                GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R32F,
                        4, 4, 4, 0, GL11.GL_RED, GL11.GL_FLOAT, volBuf)
                GL12.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL12.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)

                shader.use()
                shader.setUniformMatrix3('rotationMatrix', new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f})
                shader.setUniform('cameraDistance', 3f)
                shader.setUniform('aspectRatio', 1f)
                shader.setUniform('baseRadius', 0.8f)
                shader.setUniform('radialExaggeration', 0.2f)
                shader.setUniform('numSteps', 32)
                shader.setUniform('colorMapMode', 0)
                shader.setUniform('globalOpacity', 1f)
                shader.setUniform('showProminences', 0)
                shader.setUniform('diskCenterU', 0.5f)
                shader.setUniform('diskCenterV', 0.5f)
                shader.setUniform('diskRadiusU', 0.5f)
                shader.setUniform('diskRadiusV', 0.5f)
                shader.setUniform('lineCenterDepth', 0.5f)
                shader.setUniform('volumeTexture', 0)

                GL11.glClearColor(0f, 0f, 0f, 1f)
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6)
                GL11.glFinish()

                int err = GL11.glGetError()
                def pixels = BufferUtils.createByteBuffer(64 * 64 * 4)
                GL11.glReadPixels(0, 0, 64, 64, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
                int maxLuma = 0
                int center = (32 * 64 + 32) * 4
                for (int i = 0; i < 4; i++) {
                    int v = pixels.get(center + i) & 0xFF
                    if (v > maxLuma) maxLuma = v
                }
                shader.unbind()
                GL30.glBindVertexArray(0)
                GL30.glDeleteVertexArrays(vao)
                GL15.glDeleteBuffers(vbo)
                GL11.glDeleteTextures(volTex)
                GL11.glDeleteTextures(colorTex)
                GL30.glDeleteRenderbuffers(depthRb)
                GL30.glDeleteFramebuffers(fbo)
                shader.dispose()
                if (err != GL11.GL_NO_ERROR) {
                    return "GL error 0x${Integer.toHexString(err)}".toString()
                }
                return "centerLuma=$maxLuma".toString()
            } finally {
                GLFW.glfwDestroyWindow(window)
            }
        }

        then:
        System.err.println("[volume probe] result=$result errors=$errors")
        result.startsWith('centerLuma=')
        // expect non-black output: with intensity=0.7, mono colormap, and a centered ray, center pixel should be > 0
        Integer.parseInt(result.substring('centerLuma='.length())) > 0
    }

    def "GL 3.2 core + forward-compat context creates and runs a textured shader draw under JavaFX"() {
        when:
        def result = runOffMain {
            if (!GLFW.glfwInit()) {
                return 'glfwInit failed'
            }
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

            long window = GLFW.glfwCreateWindow(1, 1, '', 0L, 0L)
            if (window == 0L) {
                return 'glfwCreateWindow returned 0'
            }
            try {
                GLFW.glfwMakeContextCurrent(window)
                GL.createCapabilities()

                def shader = new ShaderProgram()
                shader.compile(
                        ShaderProgram.loadShaderSource('/me/champeau/a4j/jsolex/app/shaders/textured_sphere.vert'),
                        ShaderProgram.loadShaderSource('/me/champeau/a4j/jsolex/app/shaders/textured_sphere.frag'))

                def mesh = SphereMeshBuilder.buildHemisphere(16)
                int vao = GL30.glGenVertexArrays()
                GL30.glBindVertexArray(vao)

                int vbo = GL15.glGenBuffers()
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
                def posBuf = BufferUtils.createFloatBuffer(mesh.positions().length)
                posBuf.put(mesh.positions()).flip()
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, posBuf, GL15.GL_STATIC_DRAW)

                int posLoc = GL20.glGetAttribLocation(shader.programId, 'position')
                GL20.glEnableVertexAttribArray(posLoc)
                GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 0, 0L)

                int ebo = GL15.glGenBuffers()
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo)
                def idxBuf = BufferUtils.createIntBuffer(mesh.indices().length)
                idxBuf.put(mesh.indices()).flip()
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL15.GL_STATIC_DRAW)

                int tex = GL11.glGenTextures()
                GL13.glActiveTexture(GL13.GL_TEXTURE0)
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
                def pixel = BufferUtils.createByteBuffer(4)
                pixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip()
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel)

                shader.use()
                shader.setUniformMatrix4('mvp', GlMatrix.identity())
                shader.setUniform('diskCenter', 0.5f, 0.5f)
                shader.setUniform('diskRadius', 0.5f, 0.5f)
                shader.setUniform('tint', 1f, 1f, 1f, 1f)
                shader.setUniform('tex', 0)
                shader.setUniform('discardOutOfRange', 0)

                GL11.glClearColor(0f, 0f, 0f, 1f)
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
                GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.indexCount(), GL11.GL_UNSIGNED_INT, 0L)
                GL11.glFinish()

                int err = GL11.glGetError()
                shader.unbind()
                GL30.glBindVertexArray(0)
                GL30.glDeleteVertexArrays(vao)
                GL15.glDeleteBuffers(vbo)
                GL15.glDeleteBuffers(ebo)
                GL11.glDeleteTextures(tex)
                shader.dispose()
                return err == GL11.GL_NO_ERROR ? 'ok' : "GL error 0x${Integer.toHexString(err)}".toString()
            } finally {
                GLFW.glfwDestroyWindow(window)
            }
        }

        then:
        result == 'ok'
        errors.findAll { !it.contains('NSGL') }.isEmpty()
    }

    private static <T> T runOffMain(Closure<T> body) {
        def result = new Object[1]
        def err = new Throwable[1]
        def t = new Thread({
            try { result[0] = body.call() } catch (Throwable e) { err[0] = e }
        }, 'glfw-probe')
        t.start()
        t.join(30_000)
        if (err[0] != null) throw err[0]
        return (T) result[0]
    }
}
