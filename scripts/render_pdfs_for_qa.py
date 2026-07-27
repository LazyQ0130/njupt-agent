from pathlib import Path

import fitz


ROOT = Path(__file__).resolve().parents[1] / "output" / "curated_knowledge"
PAIRS = (
    (
        ROOT / "南邮学生常用政策与办事知识_人工筛选版.pdf",
        ROOT / "qa_common",
    ),
    (
        ROOT / "南邮学院与专业导航_2025招生口径_人工筛选版.pdf",
        ROOT / "qa_college",
    ),
)

for pdf_path, output_dir in PAIRS:
    output_dir.mkdir(parents=True, exist_ok=True)
    pdf = fitz.open(pdf_path)
    for index, page in enumerate(pdf):
        pixmap = page.get_pixmap(matrix=fitz.Matrix(1.6, 1.6), alpha=False)
        pixmap.save(output_dir / f"page-{index + 1}.png")
    print(f"{pdf_path.name}: {len(pdf)} pages")
