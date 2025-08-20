package com.frames;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import java.io.*;

/**
 * Classe que extende Janela e implementa as funcionalidades para modelagem
 * baseada na teoria de redes de filas
 * @author Andr    Felipe Rodrigues
 *
 */
public class JanelaRedes {
	
	/* tipoNo de qualquer centro de servi   o     o mesmo valor - tipoNo serve para o modulo avaliador */
	
	public JanelaRedes()
	{
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
 		Javalin app = Javalin.create().start(8002);
	app.post("/executar", ctx -> {
            // 1. Recebe o parâmetro string (ex: nome do usuário)
            String lang = ctx.formParam("lang");
            if (lang == null) {
                ctx.status(400).result("Parâmetro 'lang' é obrigatório");
                return;
            }

            // 2. Recebe o arquivo
            UploadedFile arquivo = ctx.uploadedFile("arquivo");
            if (arquivo == null) {
                ctx.status(400).result("Arquivo 'arquivo' é obrigatório");
                return;
            }

            // Garante que o diretório /app/tmp exista
            File tmpDir = new File("/app/tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
                System.out.println("Diretório /app/tmp criado.");
            }

            // Limpa arquivos temporários antigos
            Files.deleteIfExists(Path.of("/app/tmp/untitled.c"));
            Files.deleteIfExists(Path.of("/app/tmp/untitled"));
            Files.deleteIfExists(Path.of("untitled.out")); // Arquivo de saída temporário

            // Extrai o modelo.c para untitled.c e então o substitui pelo arquivo enviado
            JanelaRedes.extrairParaTmp("exec/smpl/modelo.c", "untitled.c");
            File fsrc = new File("/app/tmp/untitled.c");
            String originalName = arquivo.filename(); // Ex: untitled.c
            String extensao = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";

            try (InputStream in = arquivo.content(); FileOutputStream out = new FileOutputStream(fsrc)) {
                in.transferTo(out);
                System.out.println("Arquivo enviado salvo em: " + fsrc.getAbsolutePath());
            }

            if (!fsrc.exists()) {
                throw new FileNotFoundException("Arquivo " + fsrc.getAbsolutePath() + " não encontrado após upload.");
            }

            String[] comandoCompilar = new String[0];
            if (lang.equals("C SMPL")) {
                System.out.println("Modo de compilação: C SMPL");
                JanelaRedes.extrairParaTmp("exec/smpl/smpl.c", "smpl.c");
                JanelaRedes.extrairParaTmp("exec/smpl/smpl.h", "smpl.h");
                JanelaRedes.extrairParaTmp("exec/smpl/rand.c", "rand.c");
                JanelaRedes.extrairParaTmp("exec/smpl/bmeans.c", "bmeans.c");
				System.out.println(System.getenv("PATH"));

                Path tempDir = Files.createTempDirectory("sessao"); // /tmp/sessao123
String sessaoDir = tempDir.toAbsolutePath().toString();

// Salvar arquivos do usuário em sessaoDir...

String[] comandoDocker = {
    "/snap/bin/docker", "run", "--rm",
    "-v", sessaoDir + ":/app",
    "--memory=256m",
    "--cpus=1",
    "--pids-limit=50",
    "--network=none",
    "gcc:12",
    "bash", "-c",
    "cc -I /app/tmp -o /app/tmp/untitled /app/tmp/*.c -lm && /app/tmp/untitled"
};

Process p = new ProcessBuilder(comandoDocker)
        .redirectErrorStream(true)
        .start();

// Capturar saída
try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}

            } else if (lang.equals("C SMPLX")) {
                System.out.println("Modo de compilação: C SMPLX");
                JanelaRedes.extrairParaTmp("exec/smplx/smplx.c", "smplx.c");
                JanelaRedes.extrairParaTmp("exec/smplx/smplx.h", "smplx.h");
                JanelaRedes.extrairParaTmp("exec/smplx/randpar.c", "randpar.c");
                JanelaRedes.extrairParaTmp("exec/smplx/randpar.h", "randpar.h");
                JanelaRedes.extrairParaTmp("exec/smplx/declaracoes.h", "declaracoes.h");

                // Verificações de existência de arquivos SMPLX
                File fSmplx_c = new File("/app/tmp/smplx.c");
                File fRandpar_c = new File("/app/tmp/randpar.c");
                File fRandpar_h = new File("/app/tmp/randpar.h");
                File fDeclaracoes_h = new File("/app/tmp/declaracoes.h");

                System.out.println("Verificando arquivos SMPLX em /app/tmp:");
                System.out.println("- smplx.c existe? " + fSmplx_c.exists() + " | Caminho: " + fSmplx_c.getAbsolutePath());
                System.out.println("- randpar.c existe? " + fRandpar_c.exists() + " | Caminho: " + fRandpar_c.getAbsolutePath());
                System.out.println("- randpar.h existe? " + fRandpar_h.exists() + " | Caminho: " + fRandpar_h.getAbsolutePath());
                System.out.println("- declaracoes.h existe? " + fDeclaracoes_h.exists() + " | Caminho: " + fDeclaracoes_h.getAbsolutePath());

                comandoCompilar = new String[]{
                    "cc", "-I", "/app/tmp",
                    "-o", "/app/tmp/untitled",
                    "/app/tmp/untitled.c",
                    "/app/tmp/smplx.c",
                    "/app/tmp/randpar.c",
                    "-lm"
                };
            } else {
                ctx.status(400).result("Linguagem '" + lang + "' não suportada.");
                return;
            }

            try {
                System.out.println("Comando de compilação: " + String.join(" ", comandoCompilar));
                Process p = new ProcessBuilder(comandoCompilar).redirectErrorStream(true).start();
                String compilationOutput = printSaida("gcc", p.getInputStream()); // Captura a saída da compilação
                int exitCode = p.waitFor();
                System.out.println("Compilação finalizada com código de saída: " + exitCode);

                File bin = new File("/app/tmp/untitled");
                if (!bin.exists()) {
                    System.err.println("Erro: Binário não gerado após compilação.");
                    ctx.status(500).result("Compilação falhou: binário não gerado.\nSaída do compilador:\n" + compilationOutput);
                    return; // Retorna imediatamente se a compilação falhar
                }
                bin.setExecutable(true);
                System.out.println("Binário compilado e tornado executável: " + bin.getAbsolutePath());

                // Limpa o arquivo de saída anterior, se existir
                Files.deleteIfExists(Path.of("untitled.out"));

                // Executar binário
                System.out.println("Executando binário: /app/tmp/untitled");
                ProcessBuilder builder = new ProcessBuilder("/app/tmp/untitled");
                builder.redirectErrorStream(true);
                Process p2 = builder.start();
                String executionOutput = printSaida("exec", p2.getInputStream()); // Captura a saída da execução
                int execExitCode = p2.waitFor();
                System.out.println("Execução finalizada com código de saída: " + execExitCode);

                // Se houver saída de execução, você pode querer retorná-la também
                // Por enquanto, o código original espera por untitled.out
                File outputFile = new File("untitled.out");
                if (!outputFile.exists()) {
                    System.err.println("Erro: Arquivo de saída 'untitled.out' não foi gerado pela execução.");
                    ctx.status(500).result("Execução falhou: arquivo de saída 'untitled.out' não gerado.\nSaída da execução:\n" + executionOutput);
                    return;
                }

                String uuid = UUID.randomUUID().toString().replace("-", "");
                Path finalOutputPath = Path.of("/tmp", uuid + ".out");
                Files.move(Path.of("untitled.out"), finalOutputPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Arquivo de saída movido para: " + finalOutputPath.toAbsolutePath());

                // 6. Envia o arquivo de volta como download
                ctx.contentType("application/octet-stream");
                ctx.header("Content-Disposition", "attachment; filename=\"rel.out\"");
                ctx.result(new FileInputStream(finalOutputPath.toFile()));

            } catch (IOException eio) {
                System.err.println("Erro de I/O durante compilação/execução: " + eio.getMessage());
                eio.printStackTrace();
                ctx.status(500).result("Erro de I/O: " + eio.getMessage());
            } catch (InterruptedException e1) {
                System.err.println("Processo interrompido: " + e1.getMessage());
                e1.printStackTrace();
                ctx.status(500).result("Processo interrompido: " + e1.getMessage());
            } catch (RuntimeException re) {
                System.err.println("Erro em tempo de execução: " + re.getMessage());
                re.printStackTrace();
                ctx.status(500).result("Erro em tempo de execução: " + re.getMessage());
            }
        });
    }

    // Método auxiliar para copiar arquivos (não diretamente usado no endpoint, mas mantido)
    private static void copiarArquivos(File f1, File f2) {
        try {
            String temp;
            BufferedReader ori = new BufferedReader(new FileReader(f1));
            FileWriter dest = new FileWriter(f2, true);
            while ((temp = ori.readLine()) != null) {
                temp = temp + "\n";
                dest.write(temp);
            }
            ori.close();
            dest.close();
        } catch (FileNotFoundException e) {
            System.err.println("Erro: Arquivo não encontrado para cópia: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Erro de I/O durante cópia de arquivo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método auxiliar para extrair recursos do classpath para o diretório temporário
    public static File extrairParaTmp(String caminhoInterno, String nomeArquivo) throws IOException {
        InputStream in = JanelaRedes.class.getClassLoader().getResourceAsStream(caminhoInterno);
        if (in == null) {
            throw new FileNotFoundException("Arquivo não encontrado no classpath: " + caminhoInterno);
        }
        File destino = new File("/app/tmp/" + nomeArquivo);
        destino.getParentFile().mkdirs(); // cria diretório se não existir
        Files.copy(in, destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Extraído: " + caminhoInterno + " para " + destino.getAbsolutePath());
        return destino;
    }

    // Método auxiliar modificado para capturar e retornar a saída do processo
    private static String printSaida(String prefixo, InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                output.append("[").append(prefixo).append("] ").append(linha).append("\n");
                System.out.println("[" + prefixo + "] " + linha); // Continua imprimindo no console para logs do servidor
            }
        }
        return output.toString();
    }
}

	
