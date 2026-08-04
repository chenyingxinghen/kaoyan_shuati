const STORAGE_KEY = 'openexam.seenAchievementIds';
const BOOTSTRAPPED_KEY = 'openexam.seenAchievementsBootstrapped';

const readIds = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.map((id) => String(id)).filter(Boolean));
  } catch {
    return new Set();
  }
};

const writeIds = (ids) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...ids]));
  } catch {
    /* ignore quota / private mode */
  }
};

export const isAchievementUnlockBootstrapped = () => {
  try {
    return localStorage.getItem(BOOTSTRAPPED_KEY) === '1';
  } catch {
    return false;
  }
};

export const markAchievementUnlockBootstrapped = () => {
  try {
    localStorage.setItem(BOOTSTRAPPED_KEY, '1');
  } catch {
    /* ignore */
  }
};

export const getSeenAchievementIds = () => readIds();

export const markAchievementsSeen = (ids = []) => {
  if (!ids.length) return getSeenAchievementIds();
  const next = readIds();
  ids.forEach((id) => {
    if (id) next.add(String(id));
  });
  writeIds(next);
  return next;
};

/**
 * Diff newly unlocked achievements against seen set.
 * First run silently marks all currently unlocked as seen (no ceremony flood for existing users).
 * Prefer calling bootstrapAchievementUnlockSeen() at app start so ExamResult never hits first-run path.
 */
export const bootstrapAchievementUnlockSeen = (achievements = []) => {
  if (isAchievementUnlockBootstrapped()) return getSeenAchievementIds();
  const unlocked = (Array.isArray(achievements) ? achievements : [])
    .filter((item) => item && item.unlocked && item.id)
    .map((item) => item.id);
  markAchievementsSeen(unlocked);
  markAchievementUnlockBootstrapped();
  return getSeenAchievementIds();
};

export const diffNewlyUnlocked = (achievements = []) => {
  const list = Array.isArray(achievements) ? achievements : [];
  const unlocked = list.filter((item) => item && item.unlocked && item.id);

  if (!isAchievementUnlockBootstrapped()) {
    bootstrapAchievementUnlockSeen(unlocked);
    return [];
  }

  const seen = getSeenAchievementIds();
  return unlocked.filter((item) => !seen.has(String(item.id)));
};
