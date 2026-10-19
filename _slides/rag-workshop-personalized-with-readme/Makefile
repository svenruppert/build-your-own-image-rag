.PHONY: pdf clean pdf-personalized

pdf:
	quarto render slides.qmd --to beamer

pdf-personalized:
	bash build-personalized.sh

clean:
	find . -type f \( -name "*.aux" -o -name "*.log" -o -name "*.nav" -o -name "*.out" -o -name "*.snm" -o -name "*.toc" -o -name "*.vrb" -o -name "*.fls" -o -name "*.fdb_latexmk" \) -delete
	find slides -type f -path "*/rendered/*" -name "*.png" -delete || true
	rm -rf dist .tmp
	rm -f slides.pdf
