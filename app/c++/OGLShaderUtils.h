#ifndef _OGLSHADERUTILS_H
#define _OGLSHADERUTILS_H

#include <string>

#include <GLES3/gl3.h>
#include <EGL/egl.h>


inline void clearGLErrors() { while (glGetError() != GL_NO_ERROR); }

bool isGLOk(GLenum& err, std::stringstream *strst =nullptr);

GLuint compile(const std::string& source, GLenum type, GLenum& err, std::stringstream *errbuf);

bool link(const GLuint program, GLenum& err, std::stringstream* errbuf =nullptr);

GLuint compile_link(const std::string& vertex_source, const std::string fragment_source,
                    GLuint &vertexShader, GLuint &fragmentShader,
                    GLenum& err, std::stringstream *errbuf =nullptr);

#endif //_OGLSHADERUTILS_H
