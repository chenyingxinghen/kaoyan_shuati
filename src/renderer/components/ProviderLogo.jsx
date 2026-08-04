import openaiLogo from '../assets/providers/openai.svg';
import anthropicLogo from '../assets/providers/anthropic.svg';
import deepseekLogo from '../assets/providers/deepseek.svg';
import minimaxLogo from '../assets/providers/minimax.svg';
import kimiLogo from '../assets/providers/kimi.svg';
import glmLogo from '../assets/providers/glm.svg';
import doubaoLogo from '../assets/providers/doubao.svg';
import qwenLogo from '../assets/providers/qwen.svg';

const PROVIDER_LOGOS = {
  openai: openaiLogo,
  anthropic: anthropicLogo,
  claude: anthropicLogo,
  deepseek: deepseekLogo,
  minimax: minimaxLogo,
  kimi: kimiLogo,
  glm: glmLogo,
  doubao: doubaoLogo,
  qwen: qwenLogo,
};

export function ProviderLogo({ type, size = 18, className = '' }) {
  const src = PROVIDER_LOGOS[type];
  if (!src) {
    return (
      <span
        className={`provider-logo is-fallback ${className}`.trim()}
        style={{ width: size, height: size, fontSize: Math.max(10, size * 0.55) }}
        aria-hidden="true"
      >
        API
      </span>
    );
  }
  return (
    <img
      className={`provider-logo ${className}`.trim()}
      src={src}
      alt=""
      width={size}
      height={size}
      draggable={false}
    />
  );
}

export default ProviderLogo;
