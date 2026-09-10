import type { ReactNode } from 'react';

interface LiquidBlobProps {
  className?: string;
  color?: string;
  size?: number;
  style?: React.CSSProperties;
}

export function LiquidBlob({ className = '', color = 'rgba(102,92,246,0.15)', size = 300, style }: LiquidBlobProps) {
  return (
    <div
      className={`liquid-blob animate-glow ${className}`}
      style={{
        width: size,
        height: size,
        background: color,
        ...style,
      }}
    />
  );
}

interface AmbientGradientProps {
  children?: ReactNode;
  className?: string;
}

export function AmbientGradient({ children, className = '' }: AmbientGradientProps) {
  return (
    <div className={`relative overflow-hidden ${className}`}>
      <div className="liquid-blob animate-glow" style={{
        width: 600, height: 600,
        background: 'rgba(102,92,246,0.07)',
        top: '-250px', left: '-150px',
        position: 'absolute', borderRadius: '50%', filter: 'blur(80px)', pointerEvents: 'none',
      }} />
      <div className="liquid-blob animate-glow" style={{
        width: 500, height: 500,
        background: 'rgba(56,189,248,0.05)',
        bottom: '-200px', right: '-100px',
        position: 'absolute', borderRadius: '50%', filter: 'blur(80px)', pointerEvents: 'none',
        animationDelay: '2s',
      }} />
      <div className="relative z-10">{children}</div>
    </div>
  );
}

interface GlowOrbProps {
  className?: string;
  color?: string;
  size?: number;
}

export function GlowOrb({ className = '', color = 'rgba(102,92,246,0.12)', size = 200 }: GlowOrbProps) {
  return (
    <div
      className={`absolute rounded-full pointer-events-none animate-float ${className}`}
      style={{
        width: size,
        height: size,
        background: color,
        filter: 'blur(60px)',
      }}
    />
  );
}
