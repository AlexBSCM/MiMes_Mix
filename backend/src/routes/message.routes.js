const { Router } = require('express');
const prisma = require('../prisma');
const { authMiddleware } = require('../middleware/auth');

const router = Router();
router.use(authMiddleware);

/**
 * GET /api/conversations/:id/messages
 * Сообщения диалога (постранично, 50 за раз).
 * ?cursor=msgId — пагинация назад.
 */
router.get('/:conversationId/messages', async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { cursor } = req.query;
    const limit = 50;

    // Проверяем, что пользователь — участник
    const conv = await prisma.conversation.findFirst({
      where: { id: conversationId, participants: { some: { userId: req.userId } } },
    });
    if (!conv) return res.status(404).json({ error: 'Conversation not found' });

    const messages = await prisma.message.findMany({
      where: { conversationId },
      take: limit + 1,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
      orderBy: { createdAt: 'desc' },
      include: { sender: { select: { id: true, username: true } } },
    });

    const hasMore = messages.length > limit;
    if (hasMore) messages.pop();

    res.json({
      messages: messages.reverse(),
      nextCursor: hasMore ? messages[0]?.id : null,
    });
  } catch (err) {
    console.error('Get messages error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /api/conversations/:id/messages
 * { content?, mediaUrl? }
 */
router.post('/:conversationId/messages', async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { content, mediaUrl } = req.body;

    if (!content && !mediaUrl) {
      return res.status(400).json({ error: 'content or mediaUrl required' });
    }

    const message = await prisma.message.create({
      data: {
        content: content || null,
        mediaUrl: mediaUrl || null,
        senderId: req.userId,
        conversationId,
      },
      include: { sender: { select: { id: true, username: true } } },
    });

    // Обновляем updatedAt диалога
    await prisma.conversation.update({
      where: { id: conversationId },
      data: { updatedAt: new Date() },
    });

    // Emit via Socket.IO (если io доступен)
    const io = req.app.get('io');
    if (io) {
      io.to(conversationId).emit('message:new', message);
    }

    res.status(201).json(message);
  } catch (err) {
    console.error('Create message error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;