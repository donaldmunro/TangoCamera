#ifndef _RENDERER_H_
#define _RENDERER_H_
#include <sstream>
#include <chrono>
#include <memory>
#include <atomic>

#include <GLES3/gl3.h>
#include <EGL/egl.h>

#include <tango_client_api.h>

#include "glm/glm.hpp"

using TimeType = std::chrono::_V2::system_clock::time_point;

class Renderer
//============
{
public:
   Renderer(TangoCameraId cameraId, std::atomic_bool& connected_) : camera_id(cameraId), connected(connected_) {}

   virtual ~Renderer() {};

   virtual bool init(std::stringstream& errs) =0;

   virtual inline bool initialized() =0;

   virtual void resize(int w, int h) =0;

   virtual void render() =0;

   virtual inline double width() =0;

   virtual inline double height() =0;

protected:
   TangoCameraId camera_id;
   std::atomic_bool &connected;
};

class OGLRenderer : public Renderer
//=================================
{
public:
   OGLRenderer(TangoCameraId cameraId, std::atomic_bool& connected_);

   OGLRenderer(OGLRenderer const&) = delete;
   OGLRenderer(OGLRenderer&&) = delete;
   OGLRenderer& operator=(OGLRenderer const&) = delete;
   OGLRenderer& operator=(OGLRenderer &&) = delete;

   virtual ~OGLRenderer();

   virtual bool init(std::stringstream& errs);

   virtual inline bool initialized() { return (context != nullptr); }

   virtual void resize(int w, int h);

   virtual void render();

   virtual inline double width() { return w; }

   virtual inline double height() { return h; }

protected:
   EGLContext context = nullptr;
   int imagewidth=-1, imageheight =-1;
   GLfloat w =-1, h =-1, z = -1;
   std::string vertex_shader_src =
   "#version 300 es\n"
   "uniform mat4 MVP;\n"
   "layout(location = 0) in vec3 vPosition;\n"
   "layout(location = 1) in vec2 tPosition;\n"
   "out vec2 texPosition;\n"
   "void main()\n"
   "{\n"
   "   texPosition = tPosition;\n"
   "   gl_Position = vec4((MVP * vec4(vPosition, 1.0)).xy, -1, 1);\n"
   "}";
   std::string fragment_shader_src =
   "#version 300 es\n"
   "#extension GL_OES_EGL_image_external_essl3 : require\n"
   "uniform samplerExternalOES previewSampler;\n"
   "in vec2 texPosition;\n"
   "out vec4 fragmentColor;\n"
   "void main()\n"
   "{\n"
   "   fragmentColor = texture(previewSampler, texPosition);\n"
   "}";
   GLuint vertexShader =0, fragmentShader =0, shader_program =0, camera_texture =0, keypoints_texture =0;
   GLint position_attrib, texture_attrib, MVP_uniform, textureSamplerUniform;

//   TimeType timestamp, last_timestamp;
   double timestamp, last_timestamp;

private:
   glm::mat4 MVP = glm::mat4();
   std::unique_ptr<GLfloat[]> vertices, vertex_coordinates;
   std::unique_ptr<GLshort[]> texture_faces;
};

#endif //_RENDERER_H_
