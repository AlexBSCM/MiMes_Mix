const { verifyToken } = require('./middleware/auth');

/**
 * Настраивает Socket.IO сервер.
 * Клиент подключается с ?token=JWT, после аутентификации присоединяется
 * к комнатам своих диалогов.
 */
function setupSocket(io) {
  io.use((socket, next) => {
    const token = socket.handshake.query.token;
    if (!token) return next(new Error('Authentication required'));
    try {
      const payload = verifyToken(token);
      socket.userId = payload.userId;
      next();
    } catch {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', async (socket) => {
    console.log(`Socket connected: ${socket.userId}`);

    // Присоединяемся к комнатам диалогов пользователя
    try {
      const { PrismaClient } = require('@prisma/client');
      const { PrismaBetterSqlite3 } = require('@prisma/adapter-better-sqlite3');
      const adapter = new PrismaBetterSqlite3({ url: process.env.DATABASE_URL || 'file:./dev.db' });
      const prisma = new PrismaClient({ adapter });

      const conversations = await prisma.conversation.findMany({
        where: { participants: { some: { userId: socket.userId } } },
        select: { id: true },
      });

      conversations.forEach((c) => socket.join(c.id));
      await prisma.$disconnect();
    } catch (err) {
      console.error('Socket join rooms error:', err);
    }

    socket.on('typing:start', (conversationId) => {
      socket.to(conversationId).emit('typing:start', { userId: socket.userId, conversationId });
    });

    socket.on('typing:stop', (conversationId) => {
      socket.to(conversationId).emit('typing:stop', { userId: socket.userId, conversationId });
    });

    socket.on('disconnect', () => {
      console.log(`Socket disconnected: ${socket.userId}`);
    });
  });
}

module.exports = setupSocket;