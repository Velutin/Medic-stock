package com.project.mss.service;

import org.springframework.stereotype.Service;

import com.project.mss.dto.email.EmailDTO;
import com.project.mss.producer.EmailProducer;

@Service
public class EmailService {

    private final EmailProducer emailProducer;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(EmailProducer emailProducer) {
        this.emailProducer = emailProducer;
    }

    public void sendUserRegistrationEmail(Long userId, String email) {
        EmailDTO emailDTO = new EmailDTO(
                userId,
                email,
                "Bem-vindo ao sistema de estoque",
                "Olá,\n\n" +
                "Seja bem-vindo(a) ao sistema de estoque!\n\n" +
                "Seu cadastro foi realizado com sucesso.\n\n" +
                "Atenciosamente,\n" +
                "Equipe MSS"
        );
        emailProducer.publishEmailMessage(emailDTO);
    }

    public void sendPasswordChangeConfirmationEmail(Long userId, String email) {
        EmailDTO emailDTO = new EmailDTO(
                userId,
                email,
                "Confirmação de Alteração de Senha",
                "Olá,\n\n" +
                "Sua senha foi alterada com sucesso.\n\n" +
                "Se você não realizou esta alteração, entre em contato conosco imediatamente.\n\n" +
                "Atenciosamente,\n" +
                "Equipe MSS"
        );
        emailProducer.publishEmailMessage(emailDTO);
    }

    public void sendPasswordResetEmail(Long userId, String email, String username, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        EmailDTO emailDTO = new EmailDTO(
                userId,
                email,
                "Recuperação de Senha",
                String.format("""
                Olá, %s!
                
                Você solicitou a recuperação de senha da sua conta no sistema de estoque.
                
                Clique no link abaixo para redefinir sua senha:
                %s
                
                Este link expira em 30 minutos.
                
                Se você não solicitou esta recuperação, ignore este email.
                
                Atenciosamente,
                Equipe MSS
                """, username, resetLink)
        );

        emailProducer.publishEmailMessage(emailDTO);
    }

    public void sendUserAutoRegistrationEmail(Long userId, String email, String username) {
        EmailDTO emailDTO = new EmailDTO(
                userId,
                email,
                "Cadastro Automático Realizado",
                "Olá,\n\n" +
                "Um cadastro de usuário foi criado automaticamente para você no sistema de estoque.\n\n" +
                "Seu nome de usuário é: " + username + "\n" +
                "Sua senha temporária é: temp123\n" +
                "Por favor, altere sua senha no primeiro acesso.\n\n" +
                "Atenciosamente,\n" +
                "Equipe MSS"
        );
        emailProducer.publishEmailMessage(emailDTO);
    }
}
