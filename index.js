const express = require('express');
const { createProxyMiddleware, responseInterceptor } = require('http-proxy-middleware');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');

const app = express();

// Middleware de log
function logRequest(req, serviceName) {
    if (req.method != 'OPTIONS') {
        const logLine = `[${new Date().toISOString()}] [REQUEST] ${serviceName} - ${req.method} ${req.originalUrl} ${getTokenName(req) ? ('[' + getTokenName(req) + ']') : ''}\n`;
        fs.appendFile(path.join(__dirname, 'proxy.log'), logLine, err => {
            if (err) console.error('Erreur log:', err);
        });
    }
}

function logResponse(req, proxyRes, serviceName) {
    if (req.method != 'OPTIONS') {
        const duration = Date.now() - req.startTime;
        const logLine = `[${new Date().toISOString()}] [RESPONSE] ${serviceName} - ${req.method} ${req.originalUrl} - ${proxyRes.statusCode} (${duration}ms) ${getTokenName(req) ? ('[' + getTokenName(req) + ']') : ''}\n`;
        fs.appendFile(path.join(__dirname, 'proxy.log'), logLine, err => {
            if (err) console.error('Erreur log:', err);
        });
    }
}

function getTokenName(req) {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.split(' ')[1];
        const decoded = jwt.decode(token);
        return decoded.sub;
    }
    return null;
}

// Proxy pour l'app sur 8080

app.use('/riskApp', createProxyMiddleware({
    target: 'http://localhost:8080',
    pathRewrite: { '^/riskApp': '' },
    changeOrigin: true,
    selfHandleResponse: true,
    onProxyRes: responseInterceptor(async (responseBuffer, proxyRes, req, res) => {
      const responseBody = responseBuffer.toString('utf8');
      const duration = Date.now() - req.startTime;
      console.log(`[${new Date().toISOString()}] Risk APP - ${req.method} ${req.originalUrl} - ${proxyRes.statusCode} (${duration}ms)`);
      logResponse(req, proxyRes, 'Risk APP');
      console.log('→ Réponse body:', responseBody);
  
      return responseBuffer; // Important ! renvoyer la réponse inchangée
    }),
    onProxyReq: (proxyReq, req, res) => {
      req.startTime = Date.now(); // pour calculer la durée
      logRequest(req, 'Risk APP');
    }
  }));

  app.use('/userApp', createProxyMiddleware({
    target: 'http://localhost:8081',
    pathRewrite: { '^/userApp': '' },
    changeOrigin: true,
    selfHandleResponse: true,
    onProxyRes: responseInterceptor(async (responseBuffer, proxyRes, req, res) => {
      console.log("alo salem")
      const responseBody = responseBuffer.length ? responseBuffer.toString('utf8') : '[empty]';
      const duration = Date.now() - req.startTime;
      console.log(`[${new Date().toISOString()}] User APP - ${req.method} ${req.originalUrl} - ${proxyRes.statusCode} (${duration}ms)`);
      logResponse(req, proxyRes, 'User APP');
      console.log('→ Réponse body:', responseBody);
  
      return responseBuffer; // Important ! renvoyer la réponse inchangée
    }),
    onProxyReq: (proxyReq, req, res) => {
      req.startTime = Date.now(); // pour calculer la durée
      logRequest(req, 'User APP');
    }
  }));


// Lancement du serveur proxy/logger
app.listen(8082, () => {
    console.log('Proxy logger actif sur : http://localhost:8082');
    console.log('App 8080 accessible via /app1, App 8081 via /app2');
});