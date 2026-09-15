type SiteFooterProps = {
  compact?: boolean;
};

export function SiteFooter({ compact = false }: SiteFooterProps) {
  return (
    <footer
      className={`site-footer${compact ? " site-footer-compact" : ""}`}
      aria-label="版权与联系信息"
    >
      <p className="site-footer-copy">
        本项目版权归开发者所有，未经授权请勿擅自复制或用于商业用途。
      </p>
      <p className="site-footer-contact">
        如有问题或建议，欢迎联系开发者：
        <span className="site-footer-handle">NJUPT_Q</span>
        （微信、抖音同号）
      </p>
    </footer>
  );
}
