const { Router } = require('express');
const prisma = require('../prisma');
const { authMiddleware } = require('../middleware/auth');

const router = Router();
router.use(authMiddleware);

/**
 * POST /api/conversations
 * { participantIds: [id1, id2], name?, isGroup? }
 * Для приватного чата (2 участника) проверяет дубликат.
 */
router.post('/', async (req, res) => {
  try {
    const { participantIds, name, isGroup } = req.body;
    if (!participantIds || participantIds.length === 0) {
      return res.status(400).json({ error: 'participantIds required' });
    }

    // Инициатор тоже должен быть среди участников
    const allIds = [...new Set([req.userId, ...participantIds])];
    if (allIds.length < 2) {
      return res.status(400).json({ error: 'At least 2 participants required' });
    }

    // Для приватного чата проверяем, существует ли уже такой
    if (!isGroup && allIds.length === 2) {
      const existing = await prisma.conversation.findFirst({
        where: {
          AND: allIds.map(id => ({
            participants: { some: { userId: id } },
          })),
          isGroup: false,
        },
        include: { participants: true },
      });
      if (existing) {
        return res.json(existing);
      }
    }

    const conversation = await prisma.conversation.create({
      data: {
        name: name || null,
        isGroup: isGroup || false,
        participants: {
          create: allIds.map(userId => ({ userId })),
        },
      },
      include: {
        participants: { include: { user: { select: { id: true, username: true } } } },
      },
    });

    res.status(201).json(conversation);
  } catch (err) {
    console.error('Create conversation error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/conversations
 * Список диалогов пользователя с последним сообщением.
 */
router.get('/', async (req, res) => {
  try {
    const conversations = await prisma.conversation.findMany({
      where: {
        participants: { some: { userId: req.userId } },
      },
      include: {
        participants: { include: { user: { select: { id: true, username: true } } } },
        messages: { orderBy: { createdAt: 'desc' }, take: 1 },
      },
      orderBy: { updatedAt: 'desc' },
    });

    res.json(conversations);
  } catch (err) {
    console.error('Get conversations error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/conversations/:id
 * Детали диалога.
 */
router.get('/:id', async (req, res) => {
  try {
    const conversation = await prisma.conversation.findFirst({
      where: {
        id: req.params.id,
        participants: { some: { userId: req.userId } },
      },
      include: {
        participants: { include: { user: { select: { id: true, username: true } } } },
      },
    });

    if (!conversation) return res.status(404).json({ error: 'Conversation not found' });

    res.json(conversation);
  } catch (err) {
    console.error('Get conversation error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;